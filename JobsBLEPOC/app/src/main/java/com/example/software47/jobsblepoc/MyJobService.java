package com.example.software47.jobsblepoc;

import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService;

public class MyJobService extends FrameworkJobSchedulerService {
    @NonNull
    @Override
    protected JobManager getJobManager() {
        return MyApplication.getInstance().getJobManager();
    }
}