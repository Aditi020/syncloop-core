package com.eka.middleware.scheduling;

import com.eka.middleware.template.Tenant;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class AppScheduler implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        System.out.println("App Scheduler.......");

        run();
    }


    private void run() {

    }

}