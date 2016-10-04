package com.example.software47.jobsblepoc;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;

import java.util.Random;

public class GreetJob extends Job {
    private String mGeetText;
    private int mPriority;

    protected GreetJob(int priority, String greetText) {
        super(new Params(priority).persist().groupBy("group_1").addTags(greetText));
        this.mGeetText = greetText;
        this.mPriority = priority;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() throws Throwable {
        long sleepTime = new Random().nextInt(10) * 100;
        Thread.sleep(sleepTime);
        Log.e("Greet", "Priority: " + mPriority + " Greet: " + mGeetText);
    }

    @Override
    protected void onCancel(int cancelReason, @Nullable Throwable throwable) {
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
        return RetryConstraint.createExponentialBackoff(runCount, 1000 /*base delay*/);
    }
}