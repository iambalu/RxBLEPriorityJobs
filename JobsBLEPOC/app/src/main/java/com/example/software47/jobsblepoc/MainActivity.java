package com.example.software47.jobsblepoc;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.TagConstraint;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

import static com.trello.rxlifecycle.android.ActivityEvent.DESTROY;

public class MainActivity extends RxAppCompatActivity {
    @BindView(R.id.tv_state)
    TextView tvState;
    @BindView(R.id.scan_toggle_btn)
    Button scanToggleButton;
    @BindView(R.id.scan_results)
    RecyclerView recyclerView;
    @BindView(R.id.scan_service_results)
    RecyclerView recyclerViewServices;
    private RxBleClient rxBleClient;
    private Subscription scanSubscription;
    private ScanResultsAdapter resultsAdapter;
    private RxBleDevice bleDevice;
    private Subscription connectionSubscription;
    private DiscoveryResultsAdapter adapter;
    private Subscription subscription;
    private PublishSubject<Void> disconnectTriggerSubject = PublishSubject.create();
    private RxBleConnection mRxBleConnection;
    private Subscription notificationSubscription;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("activity lifecycle", "on create");

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        rxBleClient = MyApplication.getInstance().getRxBleClient();
        configureResultList();
    }

    @OnClick(R.id.scan_toggle_btn)
    protected void onScanToggleClick(View view) {
        switch (view.getId()) {
            case R.id.scan_toggle_btn:
                if (isScanning()) {
                    scanSubscription.unsubscribe();
                } else {
                    scanSubscription = rxBleClient.scanBleDevices()
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnUnsubscribe(new Action0() {
                                @Override
                                public void call() {
                                    clearScanSubscription();
                                }
                            })
                            .subscribe(new Action1<RxBleScanResult>() {
                                @Override
                                public void call(RxBleScanResult rxBleScanResult) {
                                    recyclerView.setVisibility(View.VISIBLE);
                                    resultsAdapter.addScanResult(rxBleScanResult);
                                }
                            }, new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    onScanFailure(throwable);
                                }
                            });
                }
                updateButtonUIState();
                break;
        }
    }

    private void handleBleScanException(BleScanException bleScanException) {

        switch (bleScanException.getReason()) {
            case BleScanException.BLUETOOTH_NOT_AVAILABLE:
                Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.BLUETOOTH_DISABLED:
                Toast.makeText(this, "Enable bluetooth and try again", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.LOCATION_PERMISSION_MISSING:
                Toast.makeText(this,
                        "On Android 6.0 location permission is required. Implement Runtime Permissions", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.LOCATION_SERVICES_DISABLED:
                Toast.makeText(this, "Location services needs to be enabled on Android 6.0", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.BLUETOOTH_CANNOT_START:
            default:
                Toast.makeText(this, "Unable to start scanning", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("activity lifecycle", "on pause");


        if (isScanning()) {
            /*
             * Stop scanning in onPause callback. You can use rxlifecycle for convenience. Examples are provided later.
             */
            scanSubscription.unsubscribe();
        }
    }

    private void configureResultList() {
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        resultsAdapter = new ScanResultsAdapter();
        recyclerView.setAdapter(resultsAdapter);
        resultsAdapter.setOnAdapterItemClickListener(new ScanResultsAdapter.OnAdapterItemClickListener() {
            @Override
            public void onAdapterViewClick(View view) {
                final int childAdapterPosition = recyclerView.getChildAdapterPosition(view);
                final RxBleScanResult itemAtPosition = resultsAdapter.getItemAtPosition(childAdapterPosition);
                onAdapterItemClick(itemAtPosition);
            }
        });

        adapter = new DiscoveryResultsAdapter();
        recyclerViewServices.setHasFixedSize(true);
        recyclerViewServices.setAdapter(adapter);
        recyclerViewServices.setLayoutManager(new LinearLayoutManager(this));
        adapter.setOnAdapterItemClickListener(new DiscoveryResultsAdapter.OnAdapterItemClickListener() {
            @Override
            public void onAdapterViewClick(View view) {
                final int childAdapterPosition = recyclerViewServices.getChildAdapterPosition(view);
                final DiscoveryResultsAdapter.AdapterItem itemAtPosition = adapter.getItem(childAdapterPosition);
                notificationSubscription = Observable.fromCallable(() -> mRxBleConnection)
                        .flatMap(rxBleConnection -> rxBleConnection.setupNotification(itemAtPosition.uuid))
                        .doOnNext(notificationObservable -> runOnUiThread(MainActivity.this::notificationHasBeenSetUp))
                        .flatMap(notificationObservable -> notificationObservable)
                        .compose(bindUntilEvent(DESTROY))
                        .doOnUnsubscribe(() -> notificationSubscription = null)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(MainActivity.this::onNotificationReceived, MainActivity.this::onNotificationSetupFailure);
            }
        });
    }

    private void notificationHasBeenSetUp() {
        Toast.makeText(this, "Notifications has been set up", Toast.LENGTH_SHORT).show();
    }


    private void onNotificationReceived(byte[] bytes) {
        Toast.makeText(this, "Change: " + new String(bytes), Toast.LENGTH_SHORT).show();
    }

    private void onNotificationSetupFailure(Throwable throwable) {
        Toast.makeText(this, "Notifications error: " + throwable, Toast.LENGTH_SHORT).show();
    }

    private boolean isScanning() {
        return scanSubscription != null;
    }

    private void onAdapterItemClick(RxBleScanResult scanResults) {
        scanToggleButton.performClick();
        final String macAddress = scanResults.getBleDevice().getMacAddress();
        bleDevice = rxBleClient.getBleDevice(macAddress);
        if (subscription == null) {
            // How to listen for connection state changes
            subscription = bleDevice.observeConnectionStateChanges()
                    .compose(this.<RxBleConnectionState>bindUntilEvent(DESTROY))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<RxBleConnectionState>() {
                        @Override
                        public void call(RxBleConnectionState state) {
                            onConnectionStateChange(state);
                            Log.e("Conn State", state.toString());
                        }
                    });
        }
        connectionSubscription = bleDevice.establishConnection(this, false)
                .flatMap(new Func1<RxBleConnection, Observable<RxBleDeviceServices>>() {
                    @Override
                    public Observable<RxBleDeviceServices> call(RxBleConnection rxBleConnection) {
                        mRxBleConnection = rxBleConnection;
                        onConnectionReceived(rxBleConnection);
                        return rxBleConnection.discoverServices();
                    }
                })
                .compose(this.<RxBleDeviceServices>bindUntilEvent(DESTROY))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        connectionSubscription = null;
                        Log.e("Conn State", "connection un subscribed.");
                    }
                })
                .subscribe(new Action1<RxBleDeviceServices>() {
                    @Override
                    public void call(RxBleDeviceServices rxBleDeviceServices) {
                        recyclerViewServices.setVisibility(View.VISIBLE);
                        adapter.swapScanResult(rxBleDeviceServices);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        onConnectionFailure(throwable);
                    }
                });
    }

    private void onConnectionStateChange(RxBleConnectionState newState) {
        tvState.setText(newState.toString());
    }

    private void onScanFailure(Throwable throwable) {

        if (throwable instanceof BleScanException) {
            handleBleScanException((BleScanException) throwable);
        }
    }

    private void onConnectionFailure(Throwable throwable) {
        //noinspection ConstantConditions
        Toast.makeText(this, "Connection error: " + throwable, Toast.LENGTH_SHORT).show();
    }

    private void onConnectionReceived(RxBleConnection connection) {
        //noinspection ConstantConditions
        Log.e("Conn State", connection.toString());
    }

    private void clearScanSubscription() {
        scanSubscription = null;
        resultsAdapter.clearScanResults();
        recyclerView.setVisibility(View.GONE);
        updateButtonUIState();
    }

    private void updateButtonUIState() {
        scanToggleButton.setText(isScanning() ? "stop scan" : "start scan");
    }

    private void jobDemo() {
        JobManager jobManager = MyApplication.getInstance().getJobManager();
        for (int i = 0; i < 100; i++) {
            jobManager.addJobInBackground(new GreetJob(i % 5, "Greetings number " + i));
        }
        for (int i = 0; i < 100; i++) {
            if (i % 5 == 0) {
                jobManager.cancelJobsInBackground(null, TagConstraint.ANY, "Greetings number " + i);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e("activity lifecycle", "on start");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("activity lifecycle", "on resume");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e("activity lifecycle", "on stop");

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e("activity lifecycle", "on destroy");

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.e("activity lifecycle", "on restart");

    }
}
