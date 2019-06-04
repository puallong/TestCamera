# TestCamera
a simple camera kit

Step 1.Add it in your root build.gradle at the end of repositories:


allprojects {

		repositories {
		
			...
			maven { url 'https://www.jitpack.io' }
			
		}
		
	}
  
Step 2.Add the dependency:
  	
	dependencies {
	
	        implementation 'com.github.puallong.TestCamera:cameralib:1.0.0'
		
	}
	
Step 3. Add MyCameraView in your layout

<com.example.cameralib.MyCameraView
        android:id="@+id/camera"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="8" />
	
Step 4. 

CameraHelper cameraHelper;
MyCameraView myCameraView;

 myCameraView = findViewById(R.id.camera);
 cameraHelper = CameraHelper.of(myCameraView, getApplicationContext(), MainActivity.this);
 
  cameraHelper.takePicture(new CameraHelper.addCameraListener() {
  
                    @Override
                    public void onImage(byte[] data) {
                    
                        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                        String picturePath = "TestCameraPhoto" + ".jpg";
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

	
	
