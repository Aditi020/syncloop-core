package com.eka.middleware.scheduling;


import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.service.DataPipeline;
import org.quartz.*;

public class JobScheduler {

    private static String generateIdentification(String id,DataPipeline dataPipeline) {
        return String.format("%s-%s-ID", dataPipeline.rp.getTenant().getName(), id);
    }

    private static String generateGroup(String id,DataPipeline dataPipeline) {
        return String.format("%s-%s-GROUP", dataPipeline.rp.getTenant().getName(), id);
    }

    public static void addJob(String id,String serviceFqn,String cronExpression,String job_name,DataPipeline dataPipeline) throws SchedulerException {
        ApplicationSchedulerFactory.scheduleJob(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline),serviceFqn ,cronExpression,job_name,dataPipeline);
    }

    public static void updateJob(String id,String cronExpression, DataPipeline dataPipeline) throws SchedulerException {

        JobDetail jobDetail = ApplicationSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline));
        Scheduler scheduler = ApplicationSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());
        ApplicationSchedulerFactory.updateScheduler(jobDetail, cronExpression, scheduler);
    }

    public static void deleteJob(String id, DataPipeline dataPipeline) throws SchedulerException {

        JobDetail jobDetail = ApplicationSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline));
        Scheduler scheduler = ApplicationSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());
        ApplicationSchedulerFactory.removeJob(jobDetail.getKey(), scheduler);
    }

    public static void enableOrDisableScheduler(String id,String enabled,DataPipeline dataPipeline) throws SchedulerException {
        Scheduler scheduler = ApplicationSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());

        if ("N".equals(enabled)) {
            JobDetail jobDetail = ApplicationSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id, dataPipeline), generateGroup(id, dataPipeline));
            JobKey jobKey = ApplicationSchedulerFactory.getKey(jobDetail);
            ApplicationSchedulerFactory.removeJob(jobKey, scheduler);
        } else if ("Y".equals(enabled)) {
            JobDetail jobDetail = ApplicationSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id, dataPipeline), generateGroup(id, dataPipeline));
            JobKey jobKey = ApplicationSchedulerFactory.getKey(jobDetail);
            ApplicationSchedulerFactory.startJob(jobKey, scheduler);

        }
    }

    public static void activateScheduler(DataPipeline dataPipeline) throws SchedulerException {
        Scheduler tenantScheduler = ApplicationSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());
        if (tenantScheduler != null && !tenantScheduler.isStarted()) {
            tenantScheduler.start();
        }
    }

    public static void deactivateScheduler(DataPipeline dataPipeline) throws SchedulerException {
        Scheduler tenantScheduler = ApplicationSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());
        if (tenantScheduler != null) {
            tenantScheduler.standby();
        }
    }

}
