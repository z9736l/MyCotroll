package com.shenchu.app.utils;

/**
 * Created by admin on 16/12/14.
 */

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.shenchu.app.listener.DataListener;
import com.shenchu.app.vo.RecvData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class MessageHandler {
    private final String TAG = getClass().getSimpleName();
    private static MessageHandler mInstance;
    private boolean isRun = true;
    private Socket mSocket;
    private Timer mTimer;
    private String mIpAddress;
    private int mPort;
    private DataListener mDataListener;
    private Context mContext;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    private final static String END = "IMPALER";
    private final static String PING = "";
    private final static int BUFFER_MAX_LENGTH = 1024;
    private final static int INTEGER_LENGTH = 4;

    public final static int COMMAND_HEAR_BEAT = 0x00000001;
    public final static int COMMAND_OK = 0x7FFFFFFF;
    public final static int COMMAND_IMAGE = 0x00000003;

    private MessageHandler() {
    }

    public static MessageHandler getInstance() {
        if (mInstance == null) {
            mInstance = new MessageHandler();
        }
        return mInstance;
    }

    public void setDataListener(DataListener listener) {
        mDataListener = listener;
    }

    public void setContext(Context context) {
        mContext = context;
    }

    public void connect(final String ip, final int port) {
        isRun = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1、创建客户端Socket，指定服务器地址和端口
                    mSocket = new Socket(ip, port);
                    mInputStream = mSocket.getInputStream();
                    mOutputStream = mSocket.getOutputStream();
                    Log.d(TAG, "客户端连接成功");
                    mIpAddress = ip;
                    mPort = port;
                    DataUtils.getInstance().savePreferences("ip", ip);
                    DataUtils.getInstance().savePreferences("port", port);
                    mUIHandler.sendEmptyMessage(0);
                    while (isRun && mSocket.isConnected()) {
                        recv();
                    }
                    mSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();// 出错，打印出错信息
                    disconnect();
                }
            }
        }).start();
    }

    public void disconnect() {
        try {
            isRun = false;
            mTimer.cancel();
            mSocket.close();
            mInputStream.close();
            mOutputStream.close();
            Log.d(TAG, "客户端断开连接");
        } catch (Exception e) {
            e.printStackTrace();// 出错，打印出错信息
        }
    }

    private void recv() {
        try {
            //3、获取输入流，并读取服务器端的响应信息
            if (null != mInputStream) {
                synchronized (mInputStream) {
                    byte buffer[] = new byte[BUFFER_MAX_LENGTH];
                    byte command[] = new byte[INTEGER_LENGTH];
                    byte length[] = new byte[INTEGER_LENGTH];
                    int endLength = END.getBytes("utf-8").length;
                    byte end[] = new byte[endLength];
                    byte data[] = null;
                    RecvData recvData = new RecvData();

                    mInputStream.read(command);//读入命令
                    recvData.setCommand(byteToInt(command));
                    mInputStream.read(length);//读入长度
                    recvData.setLength(byteToInt(length));
                    int index = 0;
                    int dataLength = recvData.getLength();//数据长度
                    int recvLength = -endLength;
                    int len = -1;
                    int left = dataLength;
                    if (dataLength > 0 && dataLength < BUFFER_MAX_LENGTH * 512) {
                        Log.d(TAG, "recv: 收到图片");
                        data = new byte[dataLength];
                        while (recvLength < dataLength) {
//                            if (left > 0 && left < BUFFER_MAX_LENGTH) {
//                                buffer = new byte[left];
//                            }
                            len = mInputStream.read(buffer);
                            if (len <= 0) {
                                continue;
                            }
                            recvLength += len;
                            if (len < BUFFER_MAX_LENGTH) {
                                len -= endLength;
                            }
                            System.arraycopy(buffer, 0, data, index, len);
                            index += len;//每次增加len
                            left -= len;//每次减少len
                        }
                        recvData.setData(data);
                        //Log.d(TAG, "recv: "+index);
                    }
                    mInputStream.read(end);//读入最后的end
//                    Log.d(TAG, "length: " + recvData.getLength());
//                    Log.d(TAG, "test: " + recvLength);
                    if (recvData.getCommand() > 0) {
                        Message message = new Message();
                        message.obj = recvData;
                        mHandler.sendMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            disconnect();
            e.printStackTrace();
        }
    }

    public void send(final int command, final String content) {
        if (!isRun) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //根据输入输出流和服务端连接
                    synchronized (mOutputStream) {
                        byte[] data = content.getBytes("utf-8");
                        int length = data.length;
                        mOutputStream.write(intToBytes(command));
                        mOutputStream.write(intToBytes(length));
                        //if (length > 0) {
                        mOutputStream.write(data);
                        //}
                        mOutputStream.write(END.getBytes("utf-8"));
                        mOutputStream.flush();
                    }
                } catch (Exception e) {
                    try {
                        if (null != mOutputStream) {
                            mOutputStream.close();
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    e.printStackTrace();// 出错，打印出错信息
                }
            }
        }).start();
    }

    //收到数据包
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (null != msg.obj) {
                String message = msg.obj.toString();
                RecvData recvData = (RecvData) msg.obj;
                switch (recvData.getCommand()) {
                    case COMMAND_HEAR_BEAT:
                        //Log.d(TAG, "handleMessage: 收到心跳包");
                        break;
                    case COMMAND_OK:
                        break;
                    case COMMAND_IMAGE:
                        if (null != mContext) {
                            Toast.makeText(mContext, "Command:" + recvData.getCommand(), Toast.LENGTH_LONG).show();
                        }
//                        recvData.setCommand(COMMAND_IMAGE);
//                        recvData.setData(Drawable2Bytes(mContext.getResources().getDrawable(R.mipmap.ic_launcher)));
                        Log.d(TAG, "handleMessage: 显示图片");
                        if (null != mDataListener) {
                            byte data[] = recvData.getData();
                            if (null != data && data.length > 0) {
                                mDataListener.returnData(recvData);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    };

    //连接成功，刷新界面
    Handler mUIHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    send(0, PING);
                }
            }, 0, 20000);//心跳包20秒发一次
            mDataListener.returnData(0);
        }
    };

    //int转byte数组
    public static byte[] intToBytes(int n) {
        byte[] b = new byte[4];

        for (int i = 0; i < 4; i++) {
            b[i] = (byte) (n >> (24 - i * 8));

        }
        return b;
    }

    //byte转换为int
    public static int byteToInt(byte[] b) {
        int mask = 0xff;
        int temp = 0;
        int n = 0;
        for (int i = 0; i < b.length; i++) {
            n <<= 8;
            temp = b[i] & mask;
            n |= temp;
        }
        return n;
    }
}
