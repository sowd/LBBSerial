package tech.lifedesign.lbbseriallog;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Handler;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.punchthrough.bean.sdk.Bean;
import com.punchthrough.bean.sdk.BeanDiscoveryListener;
import com.punchthrough.bean.sdk.BeanListener;
import com.punchthrough.bean.sdk.BeanManager;
import com.punchthrough.bean.sdk.message.BeanError;
import com.punchthrough.bean.sdk.message.ScratchBank;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class BeanService extends Service {
    final static String TAG = "#####BeanService" ;
    private void loge(String msg){Log.e(TAG,msg);}
    private void logd(String msg){Log.d(TAG, msg);}

    private Handler handler;
    private Runnable runnable;

    class MyBean {
        public Bean bean ;
        public String mac , name ;
        MyBeanListener beanListener ;
    }

    private HashMap<String,MyBean> beans;

    private static final int NOTIFICATION_ID = 1 ;

    public BroadcastReceiver myReceiver;

    private BeanDiscoveryListener listener;

    private final static String MY_BROADCAST_INTENT_FROM_APP = "MY_BROADCAST_INTENT_FROM_APP";
    private final static String MY_BROADCAST_INTENT_FROM_SERVICE = "MY_BROADCAST_INTENT_FROM_SERVICE";
    private final static String MY_ACTION = "MY_ACTION";

    private final static String BEAN_NAME_AIRCON = "BeanAircon";
    private final static String BEAN_NAME_REMOCON = "BeanTOSORemocon";


    public BeanService() {
        handler = new Handler();
        beans = new HashMap<String,MyBean>();
        listener = new BeanDiscoveryListener() {
            @Override
            public void onBeanDiscovered(Bean bean, int rssi) {
                if( beans.containsKey(bean.getDevice().getAddress())) return ;
                String beanname = bean.getDevice().getName() ;

                // Allow only specific devices
                if( !beanname.equals(BEAN_NAME_AIRCON) && !beanname.equals(BEAN_NAME_REMOCON))
                    return ;

                MyBean mybean = new MyBean() ;
                mybean.bean = bean ;
                mybean.name = beanname ;
                mybean.mac = bean.getDevice().getAddress() ;
                mybean.beanListener = new MyBeanListener(mybean) ;

                beans.put(mybean.mac, mybean) ;

                mybean.bean.connect(BeanService.this , mybean.beanListener);

                logd(mybean.name + " found.") ;
            }

            @Override
            public void onDiscoveryComplete() {
                handler.post(runnable);
            }
        };

        // Check some disconnected devices and try to connect to them again.
        runnable = new Runnable() {
            @Override
            public void run() {
                boolean isDisconnected = false;
                for (Map.Entry<String,MyBean> entry : beans.entrySet()) {
                    String key = entry.getKey();
                    Bean bean = entry.getValue().bean;
                    if (!bean.isConnected()) {
                        isDisconnected = true;
                        break;
                    }
                }

                if (isDisconnected)
                    BeanManager.getInstance().startDiscovery(listener);
            }
        };

        // This BroadcastReceiver is used to receive data from another process. e.g. Unity apps.
        myReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.v(BroadcastReceiver.class.getSimpleName(), intent.getAction());

                if (!intent.getAction().equals(MY_ACTION))
                    return;

                String message = intent.getExtras().getString(MY_BROADCAST_INTENT_FROM_APP);
                if (message != null) {
                    Log.v(BroadcastReceiver.class.getSimpleName(), message);

                    for (Map.Entry<String,MyBean> entry : beans.entrySet()) {
                        String key = entry.getKey();
                        Bean bean = entry.getValue().bean ;
                        if (bean.isConnected() && bean.getDevice().getName().equals(BEAN_NAME_AIRCON)) {
                            bean.sendSerialMessage(message.getBytes());
                        }
                        Log.v(BroadcastReceiver.class.getSimpleName(), key + " : " + message);
                    }
                }

            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        return null ;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    class MyBeanListener implements BeanListener {
        private MyBean mybean ;
        public MyBeanListener(MyBean mybean) {
            super() ;
            this.mybean = mybean ;
        }
        @Override
        public void onConnected() {
            logd("connected to Bean!");

            mybean.bean.sendSerialMessage("0".getBytes());
        }

        @Override
        public void onConnectionFailed() {
            logd("onConnectionFailed: " + mybean.bean.getDevice().getName());
        }

        @Override
        public void onDisconnected() {
            logd("onDisconnected: " + mybean.bean.getDevice().getName());
        }

        static final String separator1 = "/" , separator2 = ":" ;
        private String recv_buf = "" ;
        @Override
        public void onSerialMessageReceived(byte[] data) {
            String deviceName = mybean.bean.getDevice().getName();
            logd("onSerialMessageReceived: " + deviceName);

            if (deviceName.equals(BEAN_NAME_REMOCON)) {

                String recvData = "";
                try {
                    recvData = new String(data, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();;
                }

                Intent broadcastIntent = new Intent(MY_ACTION);
                broadcastIntent.putExtra(MY_BROADCAST_INTENT_FROM_SERVICE, recvData);
                getBaseContext().sendBroadcast(broadcastIntent);
                logd("sendBroadcast: " + deviceName + " : " + recvData);
            } else {
                logd(deviceName + " : " + BEAN_NAME_REMOCON);

            }
        }


        private void onMsgSeqRecv(String msg){
            logd("onMsgSeqRecv: " + mybean.bean.getDevice().getName() + ", msg: " + msg);
        }

        @Override
        public void onScratchValueChanged(ScratchBank bank, byte[] value) {
            logd("onScratchValueChanged: " + mybean.bean.getDevice().getName() + ", value: " + value);
        }

        @Override
        public void onError(BeanError error) {
            loge("onError: " + error.toString()) ;
        }

        @Override
        public void onReadRemoteRssi(int rssi) {
            logd("onReadRemoteRssi: " + rssi) ;
        }
    }

    @Override
    public void onCreate() {
        logd("Service onCreate");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MY_ACTION);
        registerReceiver(myReceiver, intentFilter);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getText(R.string.notification_title));
        builder.setSmallIcon(R.drawable.common_ic_googleplayservices);
        //builder.setContentText(getText(R.string.xxx));
        startForeground(NOTIFICATION_ID, builder.build());

        //BeanManager.getInstance().setScanTimeout(15);  // Timeout in seconds, optional, default is 30 seconds
        BeanManager.getInstance().startDiscovery(listener);
    }

    @Override
    public void onDestroy() {
        for (Map.Entry<String,MyBean> entry : beans.entrySet()) {
            Bean bean = entry.getValue().bean ;
            if( bean.isConnected() )
                bean.disconnect();
        }
        logd("Service onDestroy");
    }

}
