package com.ebanswers.cmdlib;

import android.util.Log;

import com.ebanswers.cmdlib.protocol.ProtocolFactory;
import com.ebanswers.cmdlib.protocol.bean.PCFrames;
import com.ebanswers.cmdlib.utils.ByteUtil;
import com.ebanswers.cmdlib.utils.HexUtils;
import com.ebanswers.cmdlib.utils.LogUtils;

import org.json.JSONException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Snail on 2018/10/22.
 */

public class CommandReceiver {
    private static final String TAG = "CommandReceiver";
    private ProtocolFactory protocolFactory;
    private String function;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    public Set serialSet = Collections.synchronizedSet(new HashSet<Integer>());
    private byte[] finalData;

    public CommandReceiver(String function, ProtocolFactory factory) {
        this.function = function;
        this.protocolFactory = factory;
    }


    /**
     * 解析校验通过的帧指令
     *
     * @param buffer
     */
    public void analyzeData(final byte[] buffer) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                finalData = new byte[buffer.length];
                finalData = buffer;
                Log.d(TAG, "analyzeData: " + HexUtils.bytesToHexString(finalData));
                int index = 0;
                byte serial = 0;
                int dataLen;
                for (int i = 0; i < protocolFactory.mFrameCount; i++) {
                    PCFrames pcFrames = protocolFactory.pcFrameMap.get(i + 1);
                    if (!ConstansCommand.FRAME_DOWN_CMDDATA.equals(pcFrames.getPtype())) {
                        int pcfLen = pcFrames.getLength();
                        switch (pcFrames.getPtype()) {
                            /**
                             * 流水号
                             */
                            case ConstansCommand.FRAME_SERIAL_NUMBER:
                                serial = getValToInt(index, pcfLen);
                                serialSet.add(serial);
                                break;
                            /**
                             * 帧指令功能位
                             */
                            case ConstansCommand.FRAME_UP_CMDDATA:
                                byte[] data = null;
                                dataLen = pcFrames.getLength();
                                data = getFunctionByte(index, dataLen);
                                if (ProtocolFactory.isSupportSerialNumber()) {
                                    if (ProtocolFactory.getSerialNumber() == serial) {
                                        analyzeAllStatus(data.length, data);
                                    }
                                } else {
                                    analyzeAllStatus(data.length, data);
                                }
                                break;
                            default:
                                break;
                        }
                        index = index + pcfLen;
                    }
                }
            }
        });
    }


    /**
     * 解析全指令帧
     *
     * @param len
     * @param data
     */
    public void analyzeAllStatus(int len, byte[] data) {
        Log.d(TAG, "analyzeAllStatus: " + HexUtils.bytesToHexString(data));
        int sizeF = protocolFactory.getProtocolUpFunctions() == null ? 0 : protocolFactory.getProtocolUpFunctions().size();
        int index = 0;
        byte id = 0;
        int offset = 0;
        while (index < len * 8 && id < sizeF) {
            id++;
            int bit = 0;
            bit = protocolFactory.getUpFunctionBit(id);
            int bl = (bit / 8 + ((bit % 8 == 0) ? 0 : 1)) + 1;
            offset = index / 8;
            index += bit;
            int value = 0;
            for (int i = 1; i < bl; i++) {
                int rshift = (bl - 1 - i) * 8;
                //将改位上的值左移，取值
                int lshift = (((i + offset) * 8 - index) > 0 ? ((i + offset) * 8 - index) : 0);
                byte ret = (byte) ((data[i + offset - 1] >> lshift) & getbitYU(bit));
                //大于一个字节的 ，右移动累加
                value += (ret & 0x000000FF) << rshift;
            }
            protocolFactory.setAllFunctionsMap(protocolFactory.getUpFunctionName(id), value);
        }
        try {
            protocolFactory.notifyStatusChanges(); //通知状态更新
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private byte getbitYU(int index) {
        switch (index) {
            case 0:
                return 0x01;
            case 1:
                return 0x01;
            case 2:
                return 0x03;
            case 3:
                return 0x07;
            case 4:
                return 0x0f;
            case 5:
                return 0x1f;
            case 6:
                return 0x3f;
            case 7:
                return 0x7f;
            case 8:
                return (byte) 0xff;
        }
        return (byte) 0xff;
    }


    /**
     * 获取指定位的数据
     *
     * @param index
     * @param pcflen
     * @return
     */
    private byte getValToInt(int index, int pcflen) {
        byte[] valdata = new byte[4];
        for (int i = 0; i < pcflen; i++) {
            valdata[i] = finalData[i + index];
        }
        return (byte) ByteUtil.getInt(valdata, 0);
    }

    /**
     * 获取功能位数据数组
     *
     * @param index
     * @param pcflen
     * @return
     */
    private byte[] getFunctionByte(int index, int pcflen) {
        Log.d(TAG, "getValBytes: index = " + index + " ,pcflen = " + pcflen);
        byte[] valdata = new byte[pcflen];
        for (int i = 0; i < pcflen; i++) {
            valdata[i] = finalData[i + index];
        }
        return valdata;
    }
}
