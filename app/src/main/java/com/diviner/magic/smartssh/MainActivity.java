package com.diviner.magic.smartssh;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.lang.annotation.Target;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

/**
 * @author Magic
 */
public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getCanonicalName();
    /**
     * 核心池大小
     **/
    private static final int CORE_POOL_SIZE = 1;
    /**
     * 线程池最大线程数
     **/
    private static final int MAX_IMUM_POOL_SIZE = 255;
    /**
     * 本机IP地址-完整
     **/
    private String mDevAddress;
    /**
     * 局域网IP地址头,如:192.168.1.
     **/
    private String mLocAddress;
    /**
     * 获取当前运行环境,来执行ping,相当于windows的cmd
     **/
    private Runtime mRun = Runtime.getRuntime();
    /**
     * 进程
     **/
    private Process mProcess = null;
    /**
     * 其中 -c 1为发送的次数,-w 表示发送后等待响应的时间
     **/
    private String mPing = "ping -c 1 -w 3 ";
    /**
     * ping成功的IP地址
     **/
    private List mIpList = new ArrayList();
    /**
     * 线程池对象
     **/
    private ThreadPoolExecutor mExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scan();
    }

    /**
     * <扫描局域网内ip,找到对应服务器>
     *
     * @return
     */
    public List scan() {
        // 获取本机IP地址
        mDevAddress = getHostIP();
        // 获取本地ip前缀
        mLocAddress = getLocAddrIndex(mDevAddress);
        Log.e(TAG, "开始扫描设备,本机Ip为::" + mDevAddress);
        if (TextUtils.isEmpty(mLocAddress)) {
            Log.e(TAG, "扫描失败,请检查wifi网络");
            return null;
        }
        /**
         * 1.核心池大小 2.线程池最大线程数 3.表示线程没有任务执行时最多保持多久时间会终止
         * 4.参数keepAliveTime的时间单位,有7种取值,当前为毫秒
         * 5.一个阻塞队列,用来存储等待执行的任务,这个参数的选择也很重要,会对线程池的运行过程产生重大影响
         * ,一般来说,这里的阻塞队列有以下几种选择:
         */
        mExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_IMUM_POOL_SIZE,
                2000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue(
                CORE_POOL_SIZE));
        // 新建线程池
        // 创建256个线程分别去ping
        for (int i = 1; i < 255; i++) {
            // 存放ip最后一位地址 1-255
            final int lastAddress = i;
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    String ping = MainActivity.this.mPing + mLocAddress
                            + lastAddress;
                    String currnetIp = mLocAddress + lastAddress;
                    // 如果与本机IP地址相同,跳过
                    if (mDevAddress.equals(currnetIp))
                        return;
                    try {
                        mProcess = mRun.exec(ping);
                        int result = mProcess.waitFor();
                        Log.e(TAG, "正在扫描的IP地址为:" + currnetIp + "返回值为:" + result);
                        if (result == 0) {
                           Log.e(TAG, "扫描成功,Ip地址为:" + currnetIp);
                            mIpList.add(currnetIp);
                        } else {
                            // 扫描失败
                            Log.e(TAG, "扫描失败");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "扫描异常" + e.toString());
                    } finally {
                        if (mProcess != null)
                            mProcess.destroy();
                    }
                }
            };
            mExecutor.execute(run);
        }
        mExecutor.shutdown();
        while (true) {
            try {
                // 扫描结束,开始验证
                if (mExecutor.isTerminated()) {
                    Log.e(TAG, "扫描结束,总共成功扫描到" + mIpList.size() + "个设备.");
                    Log.e("","设备列表:"+new Gson().toJson(mIpList));
                    return mIpList;
                }
            } catch (Exception e) {
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * TODO<销毁正在执行的线程池>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
    }

    /**
     * TODO<获取本地ip地址>
     *
     * @return String
     */
    private String getLocAddress() {
        String ipaddress = "";
        try {
            Enumeration en = NetworkInterface.getNetworkInterfaces();
            // 遍历所用的网络接口
            while (en.hasMoreElements()) {
                NetworkInterface networks = (NetworkInterface) en.nextElement();
                // 得到每一个网络接口绑定的所有ip
                Enumeration address = networks.getInetAddresses();
                // 遍历每一个接口绑定的所有ip
                while (address.hasMoreElements()) {
                    InetAddress ip = (InetAddress) address.nextElement();
                    if (!ip.isLoopbackAddress()&&(ip instanceof Inet4Address)){
                        ipaddress = ip.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG,"获取本地ip地址失败");
            e.printStackTrace();
        }
        Log.e(TAG,"本机Ip:"+ipaddress);
        return ipaddress;
    }

    /**
     * 获取ip地址
     * @return hostIP
     */
    public static String getHostIP(){
        String hostIP = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()){
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration ias = ni.getInetAddresses();
                while (ias.hasMoreElements()){
                    ia = (InetAddress) ias.nextElement();
                    if (ia instanceof InetAddress){
                        //skip ipv6
                        continue;
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)){
                        hostIP = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("yao","SocketException");
            e.printStackTrace();
        }
        return hostIP;
    }

    /**
     * 获取本机IP前缀
     * @param devAddress 本机IP地址
     * @return String
     */
    private String getLocAddrIndex(String devAddress){
        if (!devAddress.equals("")){
            return devAddress.substring(0,devAddress.lastIndexOf(".")+1);
        }
        return null;
    }
}
