package com.google.android.gms.samples.vision.barcodereader;

import java.io.File;

public class Personnel {
    public String name, ID;
    public String photoPath;
    public Personnel(String ID, String name, String storageDir){
        this.name = name;
        this.ID = ID;
        photoPath = storageDir+"/"+ "personnel info/photo/"+ID+".jpg";
    }
}
