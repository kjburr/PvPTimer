private package lemon42.PvPTimer.Utils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.zip.Adler32;
import lemon42.PvPTimer.TimeItem.TimeItem;

public class Checksum {
	public static String compute(HashMap<String, TimeItem> map) {
		try {
			Adler32 chk = new Adler32();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			
			for (Entry<String, TimeItem> entry : map.entrySet()) {
				dos.writeUTF(entry.getKey());
				entry.getValue().write(dos);
			}
			dos.flush();
			
			chk.update(bos.toByteArray());
			
			dos.close();
			bos.close();
			return Long.toString(chk.getValue()) + map.entrySet().size();
		} catch (IOException e) {
			System.out.println("CHECKSUM - Error while computing checksum of HashMap. Details:");
			e.printStackTrace();
		}
		
		return "";
	}
	
	public static String compute(File f) {
		try {
			int entries = 0;
			Adler32 chk = new Adler32();
			DataInputStream s = new DataInputStream(new FileInputStream(f));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			
			while (s.available() != 0) { //Still gotta read
				dos.writeUTF(s.readUTF());
				TimeItem.read(s).write(dos);
				entries++;
			}
			dos.flush();
			
			chk.update(bos.toByteArray());
			
			dos.close();
			bos.close();
			s.close();
			return Long.toString(chk.getValue()) + entries;
		} catch (IOException e) {
			System.out.println("CHECKSUM - Error while computing checksum of file. Details:");
			e.printStackTrace();
		}
		
		return "";
	}
}
