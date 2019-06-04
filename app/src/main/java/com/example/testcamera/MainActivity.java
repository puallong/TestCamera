package com.example.testcamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.example.cameralib.CameraHelper;
import com.example.cameralib.MyCameraView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    Button btn;
    String TAG = "主函数";
    CameraHelper cameraHelper;
    MyCameraView myCameraView;
    ImageView imageView;
    Bitmap bitmap;

    private void checkPermission() {
        List<String> permissions = new LinkedList<>();
        addPermission(permissions, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        addPermission(permissions, Manifest.permission.READ_EXTERNAL_STORAGE);
        addPermission(permissions, Manifest.permission.CAMERA);
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), 1);
        }
    }

    private void addPermission(List<String> permissionList, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(permission);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
        View contentView = LayoutInflater.from(this).inflate(R.layout.activity_main, null);
        setContentView(contentView);
        checkPermission();
        btn = findViewById(R.id.btnCap);
        //camera.setSurfaceTextureListener(listener);
        myCameraView = findViewById(R.id.camera);
        imageView = findViewById(R.id.image);
        cameraHelper = CameraHelper.of(myCameraView, getApplicationContext(), MainActivity.this);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("点击了****************");
                cameraHelper.takePicture(new CameraHelper.addCameraListener() {
                    @Override
                    public void onImage(byte[] data) {
                        //  base64Image=Base64.encodeToString(data,Base64.DEFAULT);
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        imageView.setImageBitmap(bitmap);
                        //手机拍照都是存到这个路径
                        System.out.println("onImage函数实现执行了！");
                        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                        String picturePath = "TestCameraPhoto" + ".jpg";
                        //  long time = System.currentTimeMillis();
//                        String picturePath = time + ".jpg";
                        File file = new File(filePath, picturePath);
                        try {
                            FileOutputStream fileOutputStream = new FileOutputStream(file);
                            fileOutputStream.write(data);
                            fileOutputStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            }
        });
    }


}
