package com.eka.middleware.pub.util.postman;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AwsSignature {

	private String key;
	private String value;
	private String type;
}
