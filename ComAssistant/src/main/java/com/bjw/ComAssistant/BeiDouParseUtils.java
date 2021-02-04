package com.bjw.ComAssistant;

import com.bjw.bean.ComBean;

import org.json.JSONObject;

public class BeiDouParseUtils {

    public static String parse(ComBean bean,String line){

        String[] split = line.split(",");
        String s = split[2];
        if (s.equals("A")) {
            String lat = split[3];
            String mmLat = lat.substring(2);
            String ddLat = (String) lat.subSequence(0, 2);
            double latitude = Integer.parseInt(ddLat) + Double.parseDouble(mmLat) / 60;

            String lon = split[5];
            String mmLon = lon.substring(3);
            String ddLon = lon.substring(0, 3);
            double longitude = Integer.parseInt(ddLon) + Double.parseDouble(mmLon) / 60;
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("time", bean.sRecTime);
                jsonObject.put("port", bean.sComPort);
                jsonObject.put("state", "定位成功");
                jsonObject.put("code", 1);
                jsonObject.put("latitude", latitude);
                jsonObject.put("longitude", longitude);
                jsonObject.put("angle", split[10]);
                return jsonObject.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (s.equals("V")) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("time", bean.sRecTime);
                jsonObject.put("port", bean.sComPort);
                jsonObject.put("state", "定位失败");
                jsonObject.put("code", 0);
                jsonObject.put("latitude", 0.0);
                jsonObject.put("longitude", 0.0);
                jsonObject.put("angle", 0);
                return jsonObject.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            return null;
        }
        return null;
    }
}
