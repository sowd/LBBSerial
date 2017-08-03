package tech.lifedesign.lbbseriallog;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.punchthrough.bean.sdk.Bean;
import com.punchthrough.bean.sdk.BeanDiscoveryListener;
import com.punchthrough.bean.sdk.BeanListener;
import com.punchthrough.bean.sdk.BeanManager;
import com.punchthrough.bean.sdk.message.BeanError;
import com.punchthrough.bean.sdk.message.ScratchBank;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class BeanService extends Service {
    final static String TAG = "#####BeanService" ;
    private void loge(String msg){Log.e(TAG,msg);}
    private void logd(String msg){Log.d(TAG, msg);}

    public BeanService() {
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

        }

        @Override
        public void onDisconnected() {

        }

        static final String separator1 = "/" , separator2 = ":" ;
        private String recv_buf = "" ;
        @Override
        public void onSerialMessageReceived(byte[] data) {
            try {
                recv_buf += new String(data, "UTF-8");
                int sep_pos = recv_buf.indexOf(separator1) ;
                while( sep_pos >= 0 ) {
                    String msg = recv_buf.substring(0,sep_pos) ;
                    recv_buf = recv_buf.substring(sep_pos+1) ;
                    onMsgSeqRecv(msg); ;
                    sep_pos = recv_buf.indexOf(separator1) ;
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }


        private void onMsgSeqRecv(String msg){
            logd(msg) ;
        }

        @Override
        public void onScratchValueChanged(ScratchBank bank, byte[] value) {

        }

        @Override
        public void onError(BeanError error) {
            loge(error.toString()) ;
        }

        @Override
        public void onReadRemoteRssi(int rssi) {

        }
    }

    class MyBean {
        public Bean bean ;
        public String mac , name ;
        MyBeanListener beanListener ;
    }

    final HashMap<String,MyBean> beans = new HashMap<String,MyBean>();

    final int NOTIFICATION_ID = 1 ;
    @Override
    public void onCreate() {
        Log.e(TAG, "Service onCreate");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getText(R.string.notification_title));
        builder.setSmallIcon(R.drawable.common_ic_googleplayservices);
        //builder.setContentText(getText(R.string.xxx));
        startForeground(NOTIFICATION_ID, builder.build());

        BeanDiscoveryListener listener = new BeanDiscoveryListener() {
            @Override
            public void onBeanDiscovered(Bean bean, int rssi) {
                if( beans.containsKey(bean.getDevice().getAddress())) return ;
                String beanname = bean.getDevice().getName() ;
                if( !beanname.equals("BeanAircon") ) return ;
                MyBean mybean = new MyBean() ;
                mybean.bean = bean ;
                mybean.name = beanname ;
                mybean.mac = bean.getDevice().getAddress() ;
                mybean.beanListener = new MyBeanListener(mybean) ;

                beans.put(mybean.mac, mybean) ;

                mybean.bean.connect(BeanService.this , mybean.beanListener);

                logd(mybean.name+" found.") ;
            }

            @Override
            public void onDiscoveryComplete() {
                BeanManager.getInstance().forgetBeans();
                beans.clear();
                // This is called when the scan times out, defined by the .setScanTimeout(int seconds) method
                BeanManager.getInstance().startDiscovery(this);
            }
        };

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
        Log.e(TAG, "Service onDestroy");
    }

}
