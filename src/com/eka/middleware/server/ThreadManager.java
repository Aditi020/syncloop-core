package com.eka.middleware.server;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.ResourceAuthenticator;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.auth.manager.AuthorizationRequest;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.MultiPart;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;
import com.eka.middleware.template.Tenant;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.PathTemplate;

public class ThreadManager {
	// int
	// threadPool=ServiceUtils.getServerProperty("middleware.server.thread.resources");

	public static Logger LOGGER = LogManager.getLogger(MiddlewareServer.class);

	private static ExecutorService executorService = Executors.newFixedThreadPool(10);

	public static final void startNewThread(final HttpServerExchange exchange) {
		executorService.submit(() -> {

		});
	}

	public static final void processRequest(final HttpServerExchange exchange) {
		String requestAddress = exchange.toString() + "@" + Integer.toHexString(System.identityHashCode(exchange));
		AuthAccount account = null;
		try {
			account = UserProfileManager.getUserProfileManager()
					.getAccount(ServiceUtils.getCurrentLoggedInUserProfile(exchange));
		} catch (SnippetException e1) {
			exchange.getResponseSender().send("Could not fetch the profile for the active session");
			LOGGER.info(ServiceUtils.getFormattedLogLine(exchange.getRequestPath(), requestAddress, "Error"));
		}
		if (account != null) {
			String tenantName = null;
			if (account.getAuthProfile() != null && account.getAuthProfile().get("tenant") != null) {
				tenantName = (String) account.getAuthProfile().get("tenant");
			} else
				tenantName = "GUEST";
			Tenant tenant = Tenant.getTenant(tenantName);

			RuntimePipeline rp = null;
			String method = exchange.getRequestMethod().toString();
			String requestPath = method + exchange.getRequestPath();
			Boolean logTransaction = true;
			// Map<String, Object> pathParams=new HashMap<String, Object>();
			if (requestPath != null && requestPath.length() > 1) {
				String resource = null;
				Map<String, Object> pathParams = new HashMap<String, Object>();
				pathParams.put("pathParameters", "");
				// if (requestPath.startsWith(method + "/execute"))
				// resource = requestPath.replace(method + "/execute/", "");
				// else
				resource = ServiceUtils.getPathService(requestPath, pathParams, tenant);

				if (resource == null) {
					exchange.getResponseHeaders().clear();

					String content = AuthorizationRequest.getContent(exchange, requestPath.toUpperCase());

					if (content != null) {
						exchange.getResponseHeaders().add(Headers.STATUS, 200);
						exchange.getResponseSender().send(content);
					} else {
						exchange.getResponseHeaders().add(Headers.STATUS, 404);
						exchange.getResponseSender()
								.send("Server is up and running but it could not find the resource.");
					}
					return;
				}

				// pathParams.put("resourcePath", requestPath);

				try {
					exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
					logTransaction = exchange.getQueryParameters().containsKey("logTransaction");
					// "custom.GenerateUUID.main";
					String uuid = ServiceUtils.generateUUID(requestAddress + "" + System.nanoTime());

					rp = RuntimePipeline.create(tenant, uuid, null, exchange, resource, requestPath);

					boolean isAllowed = false;
					// if(account.getAuthProfile().get("groups"))
					isAllowed = ResourceAuthenticator.isConsumerAllowed(resource, account);
					if (!isAllowed) {
						if (logTransaction == true)
							LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), resource, "resource"));
						String userId = rp.getCurrentLoggedInUserProfile().getId();
						if (logTransaction == true)
							LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(),
									"User(" + userId + ") is not in a consumer group.", "permission"));
						exchange.getResponseHeaders().clear();
						exchange.getResponseHeaders().put(Headers.STATUS, 400);
						exchange.getResponseSender().send("Access Denied.");
						return;
					}
					if (logTransaction == true) {
						LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), resource, "resource"));
						LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), requestAddress, "Started"));
						LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), rp.getCorrelationId(),
								"correlationId"));
					}
					final RuntimePipeline rpf = rp;
					if (exchange.getQueryParameters() != null)
						exchange.getQueryParameters().forEach((k, v) -> {
							if (v != null) {
								if (k.endsWith("-b64")) {
									String key = k.split("-b64")[0];
									String value = new String(Base64.getDecoder().decode(v.getFirst()));
									Map<String, Object> map = ServiceUtils.jsonToMap("{\"root\":" + value + "}");
									rpf.payload.put(key, map.get("root"));
								} else
									rpf.payload.put(k, v.getFirst());
							}
						});
					// rp.payload.putAll(exchange.getQueryParameters());// put("parameters",
					// exchange.getQueryParameters());
					rp.payload.put("*requestHeaders", ServiceUtils.extractHeaders(exchange));
					((Map) rp.payload.get("*requestHeaders")).put("Authorization", "********");
					rp.payload.put("*pathParameters", pathParams.get("pathParameters"));
					final String acceptHeader = (String) (rp.dataPipeLine.getHeaders().get("Accept") == null ? "*/*"
							: rp.dataPipeLine.getHeaders().get("Accept"));
					final String contentType = (String) rp.dataPipeLine.getHeaders().get("Content-Type");
					Map map = null;
					byte body[] = null;
					String content = null;

					if (contentType != null) {
						switch (contentType.toLowerCase()) {
						case "application/json":
							body = rp.dataPipeLine.getBody();
							content = new String(body);
							if (content != null && content.trim().length() > 0)
								map = ServiceUtils.jsonToMap("{\"root\":" + content + "}");
							break;
						case "application/xml":
							body = rp.dataPipeLine.getBody();
							content = new String(body);
							if (content != null && content.trim().length() > 0)
								map = ServiceUtils.xmlToMap(content);
							break;
						case "application/yaml":
							body = rp.dataPipeLine.getBody();
							content = new String(body);
							if (content != null && content.trim().length() > 0)
								map = ServiceUtils.yamlToMap(content);
							break;
						}
						if (map != null)
							rp.dataPipeLine.put("*payload", map.get("root"));
					}

					ServiceManager.invokeJavaMethod(resource, rp.dataPipeLine);
					exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");// new
																													// HttpString("Access-Control-Allow-Origin"),
																													// "*");
					exchange.getResponseHeaders().put(HttpString.tryFromString("X-Frame-Options"), "SAMEORIGIN");
					exchange.getResponseHeaders().put(HttpString.tryFromString("X-Xss-Protection"), "0");
					if (rp.payload.get("*multiPart") != null) {
						try {
							MultiPart mp = (MultiPart) rp.payload.get("*multiPart");
							ServiceUtils.startStreaming(rp, mp);
							if (logTransaction == true)
								LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), requestAddress,
										"Ended successfully"));
							rp.destroy();
							rp = null;
						} catch (Exception e) {
							throw new SnippetException(rp.dataPipeLine, "Sending stream failed\n" + e.getMessage(), e);
						}
					} else {
						// exchange.getRequestHeaders().
						String responsePayload = null;
						if (acceptHeader.toLowerCase().contains("xml")) {
							responsePayload = rp.dataPipeLine.toXml();
							exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Type"),
									"application/xml");
						} else if (acceptHeader.toLowerCase().contains("yaml")) {
							responsePayload = rp.dataPipeLine.toYaml();
							exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Type"),
									"application/x-yaml");
						} else {
							responsePayload = rp.dataPipeLine.toJson();
							exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Type"),
									"application/json");
						}
						final String resPayload = responsePayload;
						// final RuntimePipeline rpf = rp;
						exchange.getResponseSender().send(resPayload);

						/*
						 * ExecutorService threadpool = Executors.newCachedThreadPool();
						 * 
						 * @SuppressWarnings("unchecked") Future<Long> futureTask = (Future<Long>)
						 * threadpool.submit(() -> { exchange.startBlocking();
						 * exchange.getResponseSender().send(resPayload);
						 * LOGGER.info(ServiceUtils.getFormattedLogLine(rpf.getSessionID(),
						 * requestAddress, "Ended successfully")); }); try { Thread.sleep(10); while
						 * (!futureTask.isDone()) Thread.sleep(100); } catch (InterruptedException e) {
						 * // TODO Auto-generated catch block e.printStackTrace(); }
						 * 
						 * threadpool.shutdown();
						 */
						rp.destroy();
						rp = null;
					}
				} catch (SnippetException e) {
					exchange.getResponseSender()
							.send("RequestId: " + rp.getSessionID() + "\nInternal Server error:-\n" + e.getMessage());
					LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), requestAddress, "Error"));

				} finally {
					if (rp != null) {
						if (logTransaction == true)
							LOGGER.info(ServiceUtils.getFormattedLogLine(rp.getSessionID(), requestAddress, "Ended"));
						rp.destroy();
						rp = null;
					}
				}
			}
		}
	}

}
