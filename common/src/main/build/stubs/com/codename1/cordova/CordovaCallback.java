package com.codename1.cordova;


/**
 * 
 *  @author Chen
 */
public class CordovaCallback {

	public CordovaCallback() {
	}

	public CordovaCallback(ActionListener listener) {
	}

	public void onError(String jsonStr) {
	}

	public void onError(java.util.Map json) {
	}

	public void onSuccess(String jsonStr) {
	}

	public void onSuccess(java.util.Map json) {
	}

	public void sendResult(String jsonStr) {
	}

	public void sendResult(java.util.Map json) {
	}

	public java.util.Map getResponse() {
	}

	public <any> getResponseAsync(int timeout) {
	}

	public java.util.Map getResponseAndWait(int timeout) {
	}

	public boolean isError() {
	}
}
