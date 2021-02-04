package com.bjw.ComAssistant;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.bjw.bean.AssistBean;
import com.bjw.bean.ComBean;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.KeyListener;
import android.text.method.NumberKeyListener;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;

import android_serialport_api.SerialPortFinder;

/**
 * serialport api和jni取自http://code.google.com/p/android-serialport-api/
 *
 * @author 串口助手，支持4串口同时读写
 * 程序载入时自动搜索串口设备
 * n,8,1，没得选
 */
public class ComAssistantActivity extends Activity {
    EditText editTextRecDisp, editTextLines, editTextCOMA;
    EditText editTextTimeCOMA;
    CheckBox checkBoxAutoClear, checkBoxAutoCOMA;
    Button ButtonClear, ButtonSendCOMA;
    ToggleButton toggleButtonCOMA;
    Spinner SpinnerCOMA;
    Spinner SpinnerBaudRateCOMA;
    RadioButton radioButtonTxt, radioButtonHex;
    SerialControl ComA, ComB, ComC, ComD;//4个串口
    DispQueueThread DispQueue;//刷新显示线程
    SerialPortFinder mSerialPortFinder;//串口设备搜索
    AssistBean AssistData;//用于界面数据序列化和反序列化
    int iRecLines = 0;//接收区行数
    private FileUtil fileUtil;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        fileUtil = new FileUtil();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int i = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (i != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
            } else {
                setControls();
            }
        }

        ComA = new SerialControl();
        ComB = new SerialControl();
        ComC = new SerialControl();
        ComD = new SerialControl();
        DispQueue = new DispQueueThread();
        DispQueue.start();
        AssistData = getAssistData();
        setControls();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 100) {
            boolean isAllGranted = true;
            // 判断是否所有的权限都已经授予了
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }
            if (isAllGranted) {
                // 所有的权限都授予了
                setControls();
                Log.e("err", "权限都授权了");
            } else {
                // 弹出对话框告诉用户需要权限的原因, 并引导用户去应用权限管理中手动打开权限按钮
                //容易判断错
                //MyDialog("提示", "某些权限未开启,请手动开启", 1) ;
            }
        }
    }

    @Override
    public void onDestroy() {
        saveAssistData(AssistData);
        CloseComPort(ComA);
        CloseComPort(ComB);
        CloseComPort(ComC);
        CloseComPort(ComD);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        CloseComPort(ComA);
        CloseComPort(ComB);
        CloseComPort(ComC);
        CloseComPort(ComD);
        setContentView(R.layout.main);
        setControls();
    }

    //----------------------------------------------------
    private void setControls() {
        String appName = getString(R.string.app_name);
        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo("com.bjw.ComAssistant", PackageManager.GET_CONFIGURATIONS);
            String versionName = pinfo.versionName;
//			String versionCode = String.valueOf(pinfo.versionCode);
            setTitle(appName + " V" + versionName);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        editTextRecDisp = (EditText) findViewById(R.id.editTextRecDisp);
        editTextLines = (EditText) findViewById(R.id.editTextLines);
        editTextCOMA = (EditText) findViewById(R.id.editTextCOMA);
        editTextTimeCOMA = (EditText) findViewById(R.id.editTextTimeCOMA);

        checkBoxAutoClear = (CheckBox) findViewById(R.id.checkBoxAutoClear);
        checkBoxAutoCOMA = (CheckBox) findViewById(R.id.checkBoxAutoCOMA);
        ButtonClear = (Button) findViewById(R.id.ButtonClear);
        ButtonSendCOMA = (Button) findViewById(R.id.ButtonSendCOMA);
        toggleButtonCOMA = (ToggleButton) findViewById(R.id.toggleButtonCOMA);
        SpinnerCOMA = (Spinner) findViewById(R.id.SpinnerCOMA);
        SpinnerBaudRateCOMA = (Spinner) findViewById(R.id.SpinnerBaudRateCOMA);
        radioButtonTxt = (RadioButton) findViewById(R.id.radioButtonTxt);
        radioButtonHex = (RadioButton) findViewById(R.id.radioButtonHex);

        editTextCOMA.setOnEditorActionListener(new EditorActionEvent());
        editTextTimeCOMA.setOnEditorActionListener(new EditorActionEvent());
        editTextCOMA.setOnFocusChangeListener(new FocusChangeEvent());
        editTextTimeCOMA.setOnFocusChangeListener(new FocusChangeEvent());

        radioButtonTxt.setOnClickListener(new radioButtonClickEvent());
        radioButtonHex.setOnClickListener(new radioButtonClickEvent());
        ButtonClear.setOnClickListener(new ButtonClickEvent());
        ButtonSendCOMA.setOnClickListener(new ButtonClickEvent());
        toggleButtonCOMA.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
        checkBoxAutoCOMA.setOnCheckedChangeListener(new CheckBoxChangeEvent());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.baudrates_value, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        SpinnerBaudRateCOMA.setAdapter(adapter);
        SpinnerBaudRateCOMA.setSelection(12);

        mSerialPortFinder = new SerialPortFinder();
        String[] entryValues = mSerialPortFinder.getAllDevicesPath();
        List<String> allDevices = new ArrayList<String>();
        for (int i = 0; i < entryValues.length; i++) {
            allDevices.add(entryValues[i]);
        }
        ArrayAdapter<String> aspnDevices = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, allDevices);
        aspnDevices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        SpinnerCOMA.setAdapter(aspnDevices);
        if (allDevices.size() > 0) {
            SpinnerCOMA.setSelection(0);
        }
        if (allDevices.size() > 1) {
        }
        if (allDevices.size() > 2) {
        }
        if (allDevices.size() > 3) {
        }
        SpinnerCOMA.setOnItemSelectedListener(new ItemSelectedEvent());
        SpinnerBaudRateCOMA.setOnItemSelectedListener(new ItemSelectedEvent());
        DispAssistData(AssistData);
    }

    //----------------------------------------------------串口号或波特率变化时，关闭打开的串口
    class ItemSelectedEvent implements Spinner.OnItemSelectedListener {
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            if ((arg0 == SpinnerCOMA) || (arg0 == SpinnerBaudRateCOMA)) {
                CloseComPort(ComA);
                checkBoxAutoCOMA.setChecked(false);
                toggleButtonCOMA.setChecked(false);
            }
        }

        public void onNothingSelected(AdapterView<?> arg0) {
        }

    }

    //----------------------------------------------------编辑框焦点转移事件
    class FocusChangeEvent implements EditText.OnFocusChangeListener {
        public void onFocusChange(View v, boolean hasFocus) {
            if (v == editTextCOMA) {
                setSendData(editTextCOMA);
            } else if (v == editTextTimeCOMA) {
                setDelayTime(editTextTimeCOMA);
            }
        }
    }

    //----------------------------------------------------编辑框完成事件
    class EditorActionEvent implements EditText.OnEditorActionListener {
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (v == editTextCOMA) {
                setSendData(editTextCOMA);
            } else if (v == editTextTimeCOMA) {
                setDelayTime(editTextTimeCOMA);
            }
            return false;
        }
    }

    //----------------------------------------------------Txt、Hex模式选择
    class radioButtonClickEvent implements RadioButton.OnClickListener {
        public void onClick(View v) {
            if (v == radioButtonTxt) {
                KeyListener TxtkeyListener = new TextKeyListener(Capitalize.NONE, false);
                editTextCOMA.setKeyListener(TxtkeyListener);
                AssistData.setTxtMode(true);
            } else if (v == radioButtonHex) {
                KeyListener HexkeyListener = new NumberKeyListener() {
                    public int getInputType() {
                        return InputType.TYPE_CLASS_TEXT;
                    }

                    @Override
                    protected char[] getAcceptedChars() {
                        return new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'};
                    }
                };
                editTextCOMA.setKeyListener(HexkeyListener);
                AssistData.setTxtMode(false);
            }
            editTextCOMA.setText(AssistData.getSendA());
            setSendData(editTextCOMA);
        }
    }

    //----------------------------------------------------自动发送
    class CheckBoxChangeEvent implements CheckBox.OnCheckedChangeListener {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == checkBoxAutoCOMA) {
                if (!toggleButtonCOMA.isChecked() && isChecked) {
                    buttonView.setChecked(false);
                    return;
                }
                SetLoopData(ComA, editTextCOMA.getText().toString());
                SetAutoSend(ComA, isChecked);
            }
        }
    }

    //----------------------------------------------------清除按钮、发送按钮
    class ButtonClickEvent implements View.OnClickListener {
        public void onClick(View v) {
            if (v == ButtonClear) {
                editTextRecDisp.setText("");
            } else if (v == ButtonSendCOMA) {
                sendPortData(ComA, editTextCOMA.getText().toString());
            }
        }
    }

    //----------------------------------------------------打开关闭串口
    class ToggleButtonCheckedChangeEvent implements ToggleButton.OnCheckedChangeListener {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == toggleButtonCOMA) {
                if (isChecked) {

//						ComA=new SerialControl("/dev/s3c2410_serial0", "9600");
                    ComA.setPort(SpinnerCOMA.getSelectedItem().toString());
                    ComA.setBaudRate(SpinnerBaudRateCOMA.getSelectedItem().toString());
                    OpenComPort(ComA);
                } else {
                    CloseComPort(ComA);
                    checkBoxAutoCOMA.setChecked(false);
                }
            }
        }
    }

    //----------------------------------------------------串口控制类
    private class SerialControl extends SerialHelper {

        //		public SerialControl(String sPort, String sBaudRate){
//			super(sPort, sBaudRate);
//		}
        public SerialControl() {
        }

        @Override
        protected void onDataReceived(final ComBean ComRecData) {
            //数据接收量大或接收时弹出软键盘，界面会卡顿,可能和6410的显示性能有关
            //直接刷新显示，接收数据量大时，卡顿明显，但接收与显示同步。
            //用线程定时刷新显示可以获得较流畅的显示效果，但是接收数据速度快于显示速度时，显示会滞后。
            //最终效果差不多-_-，线程定时刷新稍好一些。
            DispQueue.AddQueue(ComRecData);//线程定时刷新显示(推荐)
			/*
			runOnUiThread(new Runnable()//直接刷新显示
			{
				public void run()
				{
					DispRecData(ComRecData);
				}
			});*/
        }
    }

    //----------------------------------------------------刷新显示线程
    private class DispQueueThread extends Thread {
        private Queue<ComBean> QueueList = new LinkedList<ComBean>();

        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                final ComBean ComData;
                while ((ComData = QueueList.poll()) != null) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            DispRecData(ComData);
                        }
                    });
                    try {
                        Thread.sleep(100);//显示性能高的话，可以把此数值调小。
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public synchronized void AddQueue(ComBean ComData) {
            QueueList.add(ComData);
        }
    }

    //----------------------------------------------------刷新界面数据
    private void DispAssistData(AssistBean AssistData) {
        if (AssistData==null) {
            return;
        }
        editTextCOMA.setText(AssistData.getSendA());

        setSendData(editTextCOMA);

        if (AssistData.isTxt()) {
            radioButtonTxt.setChecked(true);
        } else {
            radioButtonHex.setChecked(true);
        }
        editTextTimeCOMA.setText(AssistData.sTimeA);

        setDelayTime(editTextTimeCOMA);

    }

    //----------------------------------------------------保存、获取界面数据
    private void saveAssistData(AssistBean AssistData) {
        AssistData.sTimeA = editTextTimeCOMA.getText().toString();
        SharedPreferences msharedPreferences = getSharedPreferences("ComAssistant", Context.MODE_PRIVATE);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(AssistData);
            String sBase64 = new String(Base64.encode(baos.toByteArray(), 0));
            SharedPreferences.Editor editor = msharedPreferences.edit();
            editor.putString("AssistData", sBase64);
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //----------------------------------------------------
    private AssistBean getAssistData() {
        SharedPreferences msharedPreferences = getSharedPreferences("ComAssistant", Context.MODE_PRIVATE);
        AssistBean AssistData = new AssistBean();
        try {
            String personBase64 = msharedPreferences.getString("AssistData", "");
            byte[] base64Bytes = Base64.decode(personBase64.getBytes(), 0);
            ByteArrayInputStream bais = new ByteArrayInputStream(base64Bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            AssistData = (AssistBean) ois.readObject();
            return AssistData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return AssistData;
    }

    //----------------------------------------------------设置自动发送延时
    private void setDelayTime(TextView v) {
        if (v == editTextTimeCOMA) {
            AssistData.sTimeA = v.getText().toString();
            SetiDelayTime(ComA, v.getText().toString());
        }
    }

    //----------------------------------------------------设置自动发送数据
    private void setSendData(TextView v) {
        if (v == editTextCOMA) {
            AssistData.setSendA(v.getText().toString());
            SetLoopData(ComA, v.getText().toString());
        }
    }

    //----------------------------------------------------设置自动发送延时
    private void SetiDelayTime(SerialHelper ComPort, String sTime) {
        ComPort.setiDelay(Integer.parseInt(sTime));
    }

    //----------------------------------------------------设置自动发送数据
    private void SetLoopData(SerialHelper ComPort, String sLoopData) {
        if (radioButtonTxt.isChecked()) {
            ComPort.setTxtLoopData(sLoopData);
        } else if (radioButtonHex.isChecked()) {
            ComPort.setHexLoopData(sLoopData);
        }
    }

    //----------------------------------------------------显示接收数据
    private void DispRecData(ComBean ComRecData) {
        StringBuilder sMsg = new StringBuilder();
        if (radioButtonTxt.isChecked()) {
            String s = new String(ComRecData.bRec);
            String[] split = s.split("\r");
            for (int i = 0; i < split.length; i++) {
                String line = split[i];
                if (line.contains("$GNRMC")) {
                    String parse = BeiDouParseUtils.parse(ComRecData, line);
                    sMsg.append(parse);
                    sMsg.append("\r\n");
                    fileUtil.write(parse);
                    fileUtil.write("\r\n");
                    break;
                }
            }
        } else if (radioButtonHex.isChecked()) {
            sMsg.append("data: " + MyFunc.ByteArrToHex(ComRecData.bRec) + "\r\n");
        }


        editTextRecDisp.append(sMsg);
        iRecLines++;
        editTextLines.setText(String.valueOf(iRecLines));
        if ((iRecLines > 500) && (checkBoxAutoClear.isChecked()))//达到500项自动清除
        {
            editTextRecDisp.setText("");
            editTextLines.setText("0");
            iRecLines = 0;
        }
    }

    //----------------------------------------------------设置自动发送模式开关
    private void SetAutoSend(SerialHelper ComPort, boolean isAutoSend) {
        if (isAutoSend) {
            ComPort.startSend();
        } else {
            ComPort.stopSend();
        }
    }

    //----------------------------------------------------串口发送
    private void sendPortData(SerialHelper ComPort, String sOut) {
        if (ComPort != null && ComPort.isOpen()) {
            if (radioButtonTxt.isChecked()) {
                // 接受数据
                ComPort.sendTxt(sOut);
            } else if (radioButtonHex.isChecked()) {
                ComPort.sendHex(sOut);
            }
        }
    }


    //----------------------------------------------------关闭串口
    private void CloseComPort(SerialHelper ComPort) {
        if (ComPort != null) {
            ComPort.stopSend();
            ComPort.close();
        }
    }

    //----------------------------------------------------打开串口
    private void OpenComPort(SerialHelper ComPort) {
        try {
            ComPort.open();
        } catch (SecurityException e) {
            ShowMessage("打开串口失败:没有串口读/写权限!");
        } catch (IOException e) {
            ShowMessage("打开串口失败:未知错误!");
        } catch (InvalidParameterException e) {
            ShowMessage("打开串口失败:参数错误!");
        }
    }

    //------------------------------------------显示消息
    private void ShowMessage(String sMsg) {
        Toast.makeText(this, sMsg, Toast.LENGTH_SHORT).show();
    }
}