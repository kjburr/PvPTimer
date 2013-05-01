private package lemon42.PvPTimer.TimeItem;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TimeItem {
	Long endTime; //Time to end protection
	TimeItemType type; //Type of protection
	
	public TimeItem(Long time, TimeItemType protectionType) {
		this.endTime = time;
		this.type = protectionType;
	}
	
	public Long getEndTime() {
		return endTime;
	}
	public boolean isProtection() {
		return type != TimeItemType.TIMEOUT;
	}
	public TimeItemType getType() {
		return type;
	}
	
	public static TimeItem read(DataInputStream s) {
		try {
			return new TimeItem(s.readLong(), TimeItemType.fromId(s.readInt()));
		} catch (IOException e) {
			System.out.println("Exception while reading data! Details: ");
			e.printStackTrace();
			return null; //Error while reading... this shouldn't happen :P
		}
	}
	public void write(DataOutputStream s) {
		try {
			s.writeLong(endTime);
			s.writeInt(type.getId());
		} catch (IOException e) {
			System.out.println("Exception while writing data! Details: ");
			e.printStackTrace();
		}
		
	}
}