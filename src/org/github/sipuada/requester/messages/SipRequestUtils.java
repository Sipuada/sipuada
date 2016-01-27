package org.github.sipuada.requester.messages;

import java.text.ParseException;
import java.util.Vector;

import org.github.sipuada.sip.SipProfile;
import org.github.sipuada.sip.SupportedMediaFormat;
import org.github.sipuada.sip.SupportedMediaFormat.SupportedMediaType;

import android.gov.nist.gnjvx.sdp.MediaDescriptionImpl;
import android.gov.nist.gnjvx.sdp.fields.AttributeField;
import android.gov.nist.gnjvx.sdp.fields.ConnectionField;
import android.gov.nist.gnjvx.sdp.fields.MediaField;
import android.gov.nist.gnjvx.sdp.fields.OriginField;
import android.gov.nist.gnjvx.sdp.fields.SDPKeywords;
import android.gov.nist.gnjvx.sdp.fields.SessionNameField;
import android.gov.nist.gnjvx.sip.address.SipUri;
import android.javax.sdp.SdpConstants;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SessionDescription;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.AuthorizationHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ProxyAuthorizationHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.Request;

public class SipRequestUtils {
	
	public static FromHeader createFromHeader(AddressFactory addressFactory, HeaderFactory headerFactory,
			String username, String sipDomain, String displayName, String tag)
					throws PeerUnavailableException, ParseException {
		SipURI fromAddress = addressFactory.createSipURI(username, sipDomain);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(displayName);
		return headerFactory.createFromHeader(fromNameAddress, tag);
	}

	public static ToHeader createToHeader(AddressFactory addressFactory, HeaderFactory headerFactory, String username,
			String sipDomain, String displayName) throws PeerUnavailableException, ParseException {
		SipURI fromAddress = addressFactory.createSipURI(username, sipDomain);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(displayName);
		return headerFactory.createToHeader(fromNameAddress, null);
	}

	public static AuthorizationHeader getAuthorizationHeader(HeaderFactory headerFactory, SipProfile sipProfile)
			throws ParseException {
		SipUri uri = new SipUri();
		uri.setHost(sipProfile.getSipDomain());

		String responseDigest = AuthorizationDigest.getDigest(sipProfile.getUsername(), sipProfile.getRealm(),
				sipProfile.getPassword(), Request.REGISTER, uri.toString(), sipProfile.getNonce());

		AuthorizationHeader auth = headerFactory.createAuthorizationHeader("Digest");
		auth.setAlgorithm("MD5");
		auth.setNonce(sipProfile.getNonce());
		auth.setRealm(sipProfile.getRealm());
		auth.setUsername(sipProfile.getUsername());
		auth.setURI(uri);
		auth.setResponse(responseDigest);

		return auth;
	}
	
	public static ProxyAuthorizationHeader getProxyAuthorizationHeader(HeaderFactory headerFactory, SipProfile sipProfile) throws ParseException {
		SipUri uri = new SipUri();
		uri.setHost(sipProfile.getSipDomain());

		String responseDigest = AuthorizationDigest.getDigest(sipProfile.getUsername(), sipProfile.getRealm(), sipProfile.getPassword(), Request.INVITE, uri.toString(), sipProfile.getNonce());

		ProxyAuthorizationHeader auth = headerFactory.createProxyAuthorizationHeader("Digest");
		auth.setAlgorithm("MD5");
		auth.setNonce(sipProfile.getNonce());
		auth.setRealm(sipProfile.getRealm());
		auth.setUsername(sipProfile.getUsername());
		auth.setURI(uri);
		auth.setResponse(responseDigest);

		return auth;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static SessionDescription createSDP(Request request, SipProfile sipProfile) throws SdpException {
		SessionDescription sdp = SdpFactory.getInstance().createSessionDescription();
		long sessionId = 0L, sessionVersion = 0L;
		String sessionName;
		if (request == null) {
			sessionId = (long) Math.random() * 100000000L;
			sessionVersion = sessionId;
			sessionName = "call";
		} else {
			SessionDescription sdpSession = SdpFactory.getInstance().createSessionDescription(new String(request.getRawContent()));
			sessionId = sdpSession.getOrigin().getSessionId();
			sessionVersion = sdpSession.getOrigin().getSessionVersion();
			sessionName = sdpSession.getSessionName().getValue();
		}

		OriginField originField = new OriginField();
		originField.setUsername(sipProfile.getDisplayName());
		originField.setSessionId(sessionId);
		originField.setSessVersion(sessionVersion);
		originField.setNetworkType(SDPKeywords.IN);
		originField.setAddressType(SDPKeywords.IPV4);
		originField.setAddress(sipProfile.getLocalIpAddress());

		SessionNameField sessionNameField = new SessionNameField();
		sessionNameField.setSessionName(sessionName);

		ConnectionField connectionField = new ConnectionField();
		connectionField.setNetworkType(SDPKeywords.IN);
		connectionField.setAddressType(SDPKeywords.IPV4);
		connectionField.setAddress(sipProfile.getLocalIpAddress());

		Vector mediaDescriptions = new Vector();

		// Add audio formats
		if(sipProfile.getSupportedMediaFormats(SupportedMediaType.AUDIO).size() > 0){
			MediaField audioField = new MediaField();
			audioField.setMediaType("audio");
			audioField.setPort(sipProfile.getLocalAudioRtpPort());
			audioField.setProtocol(SdpConstants.RTP_AVP);

			Vector<String> audioFormats = new Vector<String>();
			for (SupportedMediaFormat audioFormat : sipProfile.getSupportedMediaFormats(SupportedMediaType.AUDIO)) {
				audioFormats.add(audioFormat.getFormat() + "");
			}
			audioField.setMediaFormats(audioFormats);

			MediaDescriptionImpl audioDescription = new MediaDescriptionImpl();

			for (SupportedMediaFormat audioFormat : sipProfile.getSupportedMediaFormats(SupportedMediaType.AUDIO)) {
				AttributeField attributeField = new AttributeField();
				attributeField.setName(SdpConstants.RTPMAP);
				attributeField.setValue(audioFormat.getSDPField());
				audioDescription.addAttribute(attributeField);
			}

			AttributeField sendReceive = new AttributeField();
			sendReceive.setValue("sendrecv");
			audioDescription.addAttribute(sendReceive);

			AttributeField rtcpAttribute = new AttributeField();
			rtcpAttribute.setName("rtcp");
			rtcpAttribute.setValue(sipProfile.getLocalAudioRtcpPort() + "");
			audioDescription.addAttribute(rtcpAttribute);

			mediaDescriptions.add(audioField);
			mediaDescriptions.add(audioDescription);
		}

		// Add video formats
		if(sipProfile.getSupportedMediaFormats(SupportedMediaType.VIDEO).size() > 0){
			MediaField videoField = new MediaField();
			videoField.setMediaType("video");
			videoField.setPort(sipProfile.getLocalVideoRtpPort());
			videoField.setProtocol(SdpConstants.RTP_AVP);

			Vector<String> videoFormats = new Vector<String>();
			for (SupportedMediaFormat videoFormat : sipProfile.getSupportedMediaFormats(SupportedMediaType.VIDEO)) {
				videoFormats.add(videoFormat.getFormat() + "");
			}
			videoField.setMediaFormats(videoFormats);

			MediaDescriptionImpl videoDescription = new MediaDescriptionImpl();

			for (SupportedMediaFormat videoFormat : sipProfile.getSupportedMediaFormats(SupportedMediaType.VIDEO)) {
				AttributeField attributeField = new AttributeField();
				attributeField.setName(SdpConstants.RTPMAP);
				attributeField.setValue(videoFormat.getSDPField());
				videoDescription.addAttribute(attributeField);
			}

			AttributeField sendReceive = new AttributeField();
			sendReceive.setValue("sendrecv");
			videoDescription.addAttribute(sendReceive);

			AttributeField rtcpAttribute = new AttributeField();
			rtcpAttribute.setName("rtcp");
			rtcpAttribute.setValue(sipProfile.getLocalVideoRtcpPort() + "");
			videoDescription.addAttribute(rtcpAttribute);

			mediaDescriptions.add(videoField);
			mediaDescriptions.add(videoDescription);
		}

		sdp.setOrigin(originField);
		sdp.setSessionName(sessionNameField);
		sdp.setConnection(connectionField);
		sdp.setMediaDescriptions(mediaDescriptions);

		return sdp;
	}

}
