package org.github.sipuada.sip;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.github.sipuada.requester.SipRequestState;
import org.github.sipuada.sip.SupportedMediaFormat.SupportedMediaType;

import android.javax.sip.InvalidArgumentException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.SipProvider;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;

public class SipProfile {
	
	private String username;
	private String password;
	private String sipDomain;
	private String displayName;
	private String tag;
	private String localIpAddress;
	private int localSipPort;
	private String transport;
	private String realm;
	private String nonce;
	
	private String stunAddress;
    private int stunPort;
	private int localAudioRtpPort;
	private int localAudioRtcpPort;
	private int localVideoRtpPort;
	private int localVideoRtcpPort;
	private List<SupportedMediaFormat> supportedMediaFormats;
	
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getSipDomain() {
		return sipDomain;
	}
	
	public void setSipDomain(String sipDomain) {
		this.sipDomain = sipDomain;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
	public String getTag() {
		return tag;
	}
	
	public void setTag(String tag) {
		this.tag = tag;
	}
	
	public String getLocalIpAddress() {
		return localIpAddress;
	}
	
	public void setLocalIpAddress(String localIpAddress) {
		this.localIpAddress = localIpAddress;
	}
	
	public int getLocalSipPort() {
		return localSipPort;
	}
	
	public void setLocalSipPort(int localSipPort) {
		this.localSipPort = localSipPort;
	}
	
	public String getTransport() {
		return transport;
	}
	
	public void setTransport(String transport) {
		this.transport = transport;
	}

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String getNonce() {
		return nonce;
	}

	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

	public String getStunAddress() {
		return stunAddress;
	}

	public void setStunAddress(String stunAddress) {
		this.stunAddress = stunAddress;
	}

	public int getStunPort() {
		return stunPort;
	}

	public void setStunPort(int stunPort) {
		this.stunPort = stunPort;
	}

	public int getLocalAudioRtpPort() {
		return localAudioRtpPort;
	}

	public void setLocalAudioRtpPort(int localAudioRtpPort) {
		this.localAudioRtpPort = localAudioRtpPort;
	}

	public int getLocalAudioRtcpPort() {
		return localAudioRtcpPort;
	}

	public void setLocalAudioRtcpPort(int localAudioRtcpPort) {
		this.localAudioRtcpPort = localAudioRtcpPort;
	}

	public int getLocalVideoRtpPort() {
		return localVideoRtpPort;
	}

	public void setLocalVideoRtpPort(int localVideoRtpPort) {
		this.localVideoRtpPort = localVideoRtpPort;
	}

	public int getLocalVideoRtcpPort() {
		return localVideoRtcpPort;
	}

	public void setLocalVideoRtcpPort(int localVideoRtcpPort) {
		this.localVideoRtcpPort = localVideoRtcpPort;
	}

	public List<SupportedMediaFormat> getSupportedMediaFormats() {
		return supportedMediaFormats;
	}

	public void setSupportedMediaFormats(List<SupportedMediaFormat> supportedMediaFormats) {
		this.supportedMediaFormats = supportedMediaFormats;
	}
	
	public List<SupportedMediaFormat> getSupportedMediaFormats(final SupportedMediaType type) {
		List<SupportedMediaFormat> supportedMediaFormatsByType = new ArrayList<>();
		
		Iterator<SupportedMediaFormat> iterator = supportedMediaFormats.iterator();
		while(iterator.hasNext()) {
			SupportedMediaFormat aSupportedMediaFormat = iterator.next();
			if(aSupportedMediaFormat.getType() == type) {
				supportedMediaFormatsByType.add(aSupportedMediaFormat);
			}
		}
		
		return supportedMediaFormatsByType;
	}
	
	

}



//public Message(SipRequestState state,
//		final String transport, final String toUsername, final String toDisplayName, final String toSipDomain, int toSipPort,
//		AddressFactory addressFactory, HeaderFactory headerFactory, MessageFactory messageFactory, SipProvider sipProvider, String requestMethod, Long callSequence, Integer maxForwards)
//				throws PeerUnavailableException, ParseException, InvalidArgumentException 