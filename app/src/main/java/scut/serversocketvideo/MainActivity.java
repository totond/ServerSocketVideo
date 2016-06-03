package scut.serversocketvideo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback, Camera.PreviewCallback {
    private TextView tv = null;
    private TextView IPtv = null;
    private Button btnAcept = null;
    private Button btnSendVideo = null;
    private Socket socket;
    private ServerSocket mServerSocket = null;
    private boolean running = false;
    private AcceptThread mAcceptThread;
    private ReceiveThread mReceiveThread;
    private Handler mHandler = null;

    private  DataOutputStream dos;
    private  ByteArrayOutputStream Bos;



    private SurfaceView surface_view;

    private SurfaceHolder surfaceHolder ;
    private Camera camera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }


    private void initView(){
        tv = (TextView) findViewById(R.id.tv);
        IPtv = (TextView) findViewById(R.id.tvIP);
        btnAcept = (Button) findViewById(R.id.btnAccept);
        btnSendVideo = (Button) findViewById(R.id.btnSendVideo);


        surface_view = (SurfaceView) findViewById(R.id.surface_view);
        surface_view.getHolder().addCallback(this);
        surfaceHolder = surface_view.getHolder();
        surface_view.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


        mHandler = new MyHandler();
        setButtonOnStartState(true);//设置按钮状态
        btnAcept.setOnClickListener(this);
        //发送视频按钮
        btnSendVideo.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnAccept:
                //开始监听线程，监听客户端连接
                mAcceptThread = new AcceptThread();
                running = true;
                mAcceptThread.start();
                setButtonOnStartState(false);
                IPtv.setText("等待连接");
                break;
            case R.id.btnSendVideo:
                try {
                    dos = new DataOutputStream(socket.getOutputStream());//获取Socket输出流
                    //启动摄像头
                    camera.startPreview();
                    camera.setPreviewCallback(this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    //定义监听客户端连接的线程
    private class AcceptThread extends Thread {
        @Override
        public void run() {
//            while (running) {
            try {
                mServerSocket = new ServerSocket(40012);//建立一个ServerSocket服务器端
                socket = mServerSocket.accept();//阻塞直到有socket客户端连接
//                System.out.println("连接成功");
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Message msg = mHandler.obtainMessage();
                msg.what = 0;
                msg.obj = socket.getInetAddress().getHostAddress();//获取客户端IP地址
                mHandler.sendMessage(msg);//返回连接成功的信息
                //开启mReceiveThread线程接收数据
                mReceiveThread = new ReceiveThread(socket);
                mReceiveThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
//            }
        }
    }

    //定义接收数据的线程
    private class ReceiveThread extends Thread {
        private InputStream is = null;
        private String read;

        //建立构造函数来获取socket对象的输入流
        public ReceiveThread(Socket sk) {
            try {
                is = sk.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                try {
                    //读服务器端发来的数据，阻塞直到收到结束符\n或\r
                    read = br.readLine();
                    System.out.println(read);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    running = false;//防止服务器端关闭导致客户端读到空指针而导致程序崩溃
                    Message msg2 = mHandler.obtainMessage();
                    msg2.what = 2;
                    mHandler.sendMessage(msg2);//发送信息通知用户客户端已关闭
                    e.printStackTrace();
                    break;
                }
                //用Handler把读取到的信息发到主线程
                Message msg = mHandler.obtainMessage();
                msg.what = 1;
                msg.obj = read;
                mHandler.sendMessage(msg);

            }
        }
    }


    class MyHandler extends Handler {//在主线程处理Handler传回来的message

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    String str = (String) msg.obj;
                    tv.setText(str);
                    break;
                case 0:
                    IPtv.setText("客户端" + msg.obj + "已连接");
                    displayToast("连接成功");
                    break;
                case 2:
                    displayToast("客户端已断开");
                    //清空TextView
                    tv.setText(null);//
                    IPtv.setText(null);
                    camera.stopPreview();
                    try {
                        socket.close();
                        mServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    setButtonOnStartState(true);
                    break;

            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);//清空消息队列，防止Handler强引用导致内存泄漏
    }

    private void setButtonOnStartState(boolean flag) {//设置按钮的状态

        btnAcept.setEnabled(flag);
        btnSendVideo.setEnabled(!flag);
    }

    private void displayToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    public void surfaceCreated(SurfaceHolder holder) {

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        try{
            camera = Camera.open();
            camera.setPreviewDisplay(holder);
            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize(352, 288);
            camera.setParameters(params);
            camera.setDisplayOrientation(90);//旋转90度
            System.out.println("camera变化");


        }catch(Exception e){
            e.printStackTrace();
        }
    }
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.setPreviewCallback(null); //这个必须在前，不然退出出错
        camera.stopPreview();
        if(camera != null) camera.release() ;
        camera = null ;
    }

    public void onPreviewFrame(byte[] data, Camera camera) {
        System.out.println( "vedio data come ...");
        Camera.Size size = camera.getParameters().getPreviewSize();
        Bos = new ByteArrayOutputStream();
        YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
        image.compressToJpeg(new Rect(0, 0, size.width, size.height),80, Bos);
        byte[] jdata = Bos.toByteArray();
        ByteArrayInputStream inputstream = new ByteArrayInputStream(jdata);
        int sm = inputstream.available();

        System.out.println("sending"+dos.size());
        try {

            byte[] idata = new byte[sm];
            System.out.println("size = " + sm);
            inputstream.read(idata);
            dos.writeInt(sm);
            dos.write(idata);

//            dos.write(jdata,0,jdata.length);
            dos.flush();
            System.out.println("send out"+dos.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
//        System.out.println("start"+jdata.length);
//        Bitmap bitmap = BitmapFactory.decodeByteArray(jdata,0,jdata.length);
//        System.out.println("afterdecode"+bitmap.getWidth());
//        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

//        Canvas canvas  = new Canvas(mutableBitmap);

//        canvas.setBitmap(bitmap);
        //进行绘图操作
//        surface_view1.draw(canvas);
//        System.out.println("afterdraw"+jdata.length);


//        Canvas canvas  = surfaceHolder1.lockCanvas() ;          //获得canvas对象
//        //进行绘图操作
//        canvas.setBitmap(mutableBitmap);
//        surfaceHolder1.unlockCanvasAndPost(canvas) ;             //释放canvas锁，并且显示视图


//        System.out.println("unlock"+mutableBitmap.getWidth());
//        iv.setImageBitmap(mutableBitmap);
//
////        mutableBitmap.recycle();
//        System.out.println("fin"+mutableBitmap.getWidth());


    }
}


