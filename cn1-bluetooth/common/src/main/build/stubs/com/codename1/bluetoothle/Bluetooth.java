package com.codename1.bluetoothle;


/**
 * 
 *  @author Chen
 */
public class Bluetooth {

	public static final int SCAN_MODE_BALANCED = 1;

	public static final int SCAN_MODE_LOW_LATENCY = 2;

	public static final int SCAN_MODE_LOW_POWER = 0;

	public static final int SCAN_MODE_OPPORTUNISTIC = -1;

	public static final int MATCH_MODE_AGGRESSIVE = 1;

	public static final int MATCH_MODE_STICKY = 2;

	public static final int MATCH_NUM_ONE_ADVERTISEMENT = 1;

	public static final int MATCH_NUM_FEW_ADVERTISEMENT = 2;

	public static final int MATCH_NUM_MAX_ADVERTISEMENT = 3;

	public static final int CALLBACK_TYPE_ALL_MATCHES = 1;

	public static final int CALLBACK_TYPE_FIRST_MATCH = 2;

	public static final int CALLBACK_TYPE_MATCH_LOST = 4;

	public static final int CONNECTION_PRIORITY_LOW = 0;

	public static final int CONNECTION_PRIORITY_BALANCED = 1;

	public static final int CONNECTION_PRIORITY_HIGH = 2;

	public Bluetooth() {
	}

	public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) {
	}

	/**
	 *  Not supported by iOS.  With throw an IOException if called on iOS.
	 *  @throws IOException 
	 */
	public void enable() {
	}

	/**
	 *  Not supported by iOS.  With throw an IOException if called on iOS.
	 *  @throws IOException 
	 */
	public void disable() {
	}

	public void startScan(ActionListener callback, java.util.ArrayList services, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) {
	}

	public void stopScan() {
	}

	public void retrieveConnected(ActionListener callback, java.util.ArrayList services) {
	}

	public void connect(ActionListener callback, String address) {
	}

	public void reconnect(ActionListener callback, String address) {
	}

	public void disconnect(String address) {
	}

	public void close(String address) {
	}

	/**
	 *  Not currently supported on iOS.  Currently does nothing if called on iOS.
	 *  @param callback
	 *  @param address
	 *  @throws IOException 
	 */
	public void discover(ActionListener callback, String address) {
	}

	public void services(ActionListener callback, String address, java.util.ArrayList services) {
	}

	public void characteristics(ActionListener callback, String address, String service, java.util.ArrayList characteristics) {
	}

	public void descriptors(ActionListener callback, String address, String service, String characteristic) {
	}

	public void read(ActionListener callback, String address, String service, String characteristic) {
	}

	public void subscribe(ActionListener callback, String address, String service, String characteristic) {
	}

	public void unsubscribe(ActionListener callback, String address, String service, String characteristic) {
	}

	public void write(ActionListener callback, String address, String service, String characteristic, String value, boolean noResponse) {
	}

	public void writeQ(ActionListener callback, String address, String service, String characteristic, String value, boolean noResponse) {
	}

	public void readDescriptor(ActionListener callback, String address, String service, String characteristic, String descriptor) {
	}

	public void writeDescriptor(ActionListener callback, String address, String service, String characteristic, String descriptor, String value) {
	}

	public void rssi(ActionListener callback, String address) {
	}

	/**
	 *  Not supported by iOS.  With throw an IOException if called on iOS.
	 *  @param callback
	 *  @param address
	 *  @param mtu
	 *  @throws IOException 
	 */
	public void mtu(ActionListener callback, String address, int mtu) {
	}

	/**
	 *  Not supported by iOS.  With throw an IOException if called on iOS.
	 *  
	 *  @param callback
	 *  @param address
	 *  @param priority
	 *  @throws IOException 
	 */
	public void requestConnectionPriority(ActionListener callback, String address, int priority) {
	}

	public boolean isInitialized() {
	}

	public boolean isEnabled() {
	}

	public boolean isScanning() {
	}

	public boolean wasConnected(String address) {
	}

	public boolean isConnected(String address) {
	}

	public boolean isDiscovered(String address) {
	}

	/**
	 *  Not supported on iOS.  Will throw IOException if called on iOS.
	 *  @return
	 *  @throws IOException 
	 */
	public boolean hasPermission() {
	}

	/**
	 *  Not supported on iOS.  Will throw IOException if called on iOS.
	 *  @return
	 *  @throws IOException 
	 */
	public boolean requestPermission() {
	}

	/**
	 *  Not supported on iOS. Will throw IOException if called on iOS.
	 *  @return
	 *  @throws IOException 
	 */
	public boolean isLocationEnabled() {
	}

	/**
	 *  Not supported on iOS. Will throw IOException if called on iOS.
	 *  @return
	 *  @throws IOException 
	 */
	public boolean requestLocation() {
	}
}
