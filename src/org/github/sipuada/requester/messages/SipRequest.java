package org.github.sipuada.requester.messages;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.github.sipuada.requester.SipRequestState;
import org.github.sipuada.sip.SipContact;
import org.github.sipuada.sip.SipProfile;

import android.javax.sdp.SdpException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;

public class SipRequest {

	// Request method
	private String requestMethod;

	// To and From headers
	private FromHeader fromHeader;
	private ToHeader toHeader;

	// SIP request URI
	private SipURI requestURI;

	// Via headers
	private List<ViaHeader> viaHeaders = new ArrayList<>();

	// CallId deader
	private CallIdHeader callIdHeader;

	// Cseq header
	private CSeqHeader cSeqHeader;

	// MaxForwards header
	private MaxForwardsHeader maxFowardsHeader;
	
	private String textMessage;

	// Request
	private Request request;

	/**
	 * Constructor
	 * 
	 * @param state
	 * @param fromHeader
	 * @param toHeader
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 * @throws NullPointerException
	 * @throws SdpException
	 */
	public SipRequest(SipRequestState state, SipProfile sipProfile, SipContact sipContact, AddressFactory addressFactory,
			HeaderFactory headerFactory, MessageFactory messageFactory, SipProvider sipProvider, String requestMethod,
			Long callSequence, Integer maxForwards, Integer expirationTimeout, String textMessage)
					throws ParseException, InvalidArgumentException, SipException, SdpException {

		// Request method
		this.requestMethod = requestMethod;
		
		// create text message for Request.MESSAGE usage
		this.textMessage = textMessage;

		// create To and From headers
		this.fromHeader = SipRequestUtils.createFromHeader(addressFactory, headerFactory, sipProfile.getUsername(),
				sipProfile.getSipDomain(), sipProfile.getDisplayName(), sipProfile.getTag());
		this.toHeader = SipRequestUtils.createToHeader(addressFactory, headerFactory, sipContact.getUsername(),
				sipContact.getSipDomain(), sipContact.getDisplayName());

		// create a new Request URI
		if (Request.REGISTER == requestMethod) {
			requestURI = addressFactory.createSipURI(sipProfile.getUsername(), sipProfile.getSipDomain());
		} else if (Request.INVITE == requestMethod || Request.MESSAGE == requestMethod) {
			requestURI = addressFactory.createSipURI(sipContact.getUsername(), sipContact.getSipDomain());
			if (sipContact.isLocalNetworkContact()) {
				requestURI.setPort(sipContact.getSipPort());
			}
		}

		// add Via header
		ViaHeader viaHeader = headerFactory.createViaHeader(sipProfile.getLocalIpAddress(),
				sipProfile.getLocalSipPort(), sipProfile.getTransport(), null);

		if (Request.REGISTER == requestMethod) {
			viaHeader.setRPort();
		} else if (Request.INVITE == requestMethod || Request.MESSAGE == requestMethod) {
			if (!sipContact.isLocalNetworkContact()) {
				viaHeader.setRPort();
			}
		}
		viaHeaders.add(viaHeader);

		// create a new CallId header
		if (Request.REGISTER == requestMethod) {
			callIdHeader = sipProvider.getNewCallId();
		} else if (Request.INVITE == requestMethod || Request.MESSAGE == requestMethod) {
			if (state.equals(SipRequestState.REGISTER))
				callIdHeader = sipProvider.getNewCallId();
		}

		// create a new CSeq header
		cSeqHeader = headerFactory.createCSeqHeader((null == callSequence ? 1L : callSequence), requestMethod);

		// create a new MaxForwards Header
		maxFowardsHeader = headerFactory.createMaxForwardsHeader((null == maxForwards ? 70 : maxForwards));

		// create the request
		request = makeRequest(messageFactory, requestURI, requestMethod, callIdHeader, cSeqHeader, fromHeader, toHeader,
				viaHeaders, maxFowardsHeader);

		if (Request.REGISTER == requestMethod) {
			if (state.equals(SipRequestState.UNREGISTER) || state.equals(SipRequestState.UNREGISTER_AUTHORIZATION)) {
				// Create a new Expires header
				ExpiresHeader expires = headerFactory.createExpiresHeader(0);
				request.addHeader(expires);

				// Create an empty Contact header
				ContactHeader contactHeader = headerFactory.createContactHeader();
				request.addHeader(contactHeader);
			} else {
				// Create the contact name address
				SipURI contactURI = addressFactory.createSipURI(sipContact.getUsername(), sipContact.getSipDomain());
				contactURI.setPort(sipContact.getSipPort());
				Address contactAddress = addressFactory.createAddress(contactURI);
				contactAddress.setDisplayName(sipContact.getDisplayName());

				// Create a new Contact header
				ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
				contactHeader.setExpires((null == expirationTimeout ? 300 : expirationTimeout));
				request.addHeader(contactHeader);
			}

			if (state.equals(SipRequestState.AUTHORIZATION) || state.equals(SipRequestState.UNREGISTER_AUTHORIZATION)) {
				request.addHeader(SipRequestUtils.getAuthorizationHeader(headerFactory, sipProfile));
			}

		} else if (Request.INVITE == requestMethod || Request.MESSAGE == requestMethod) {

			if (Request.MESSAGE == requestMethod) {
				SupportedHeader supportedHeader = headerFactory.createSupportedHeader("replaces, outbound");
				request.addHeader(supportedHeader);
			}

			// Create a new Expires header
			ExpiresHeader expires = headerFactory
					.createExpiresHeader((null == expirationTimeout ? 300 : expirationTimeout));
			request.addFirst(expires);

			SipURI contactURI = addressFactory.createSipURI(sipProfile.getUsername(), sipProfile.getSipDomain());
			contactURI.setPort(sipProfile.getLocalSipPort());
			Address contactAddress = addressFactory.createAddress(contactURI);
			contactAddress.setDisplayName(sipProfile.getDisplayName());

			// Create a new Contact header
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			request.addHeader(contactHeader);

			if (state.equals(SipRequestState.AUTHORIZATION)) {
				request.addHeader(SipRequestUtils.getProxyAuthorizationHeader(headerFactory, sipProfile));
			}

			if (Request.INVITE == requestMethod) {
				ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
				SessionDescription sdp = SipRequestUtils.createSDP(null, sipProfile);
				request.setContent(sdp, contentTypeHeader);
			} else if (Request.MESSAGE == requestMethod) {
				ContentTypeHeader contentTypeHeader = headerFactory
	                    .createContentTypeHeader("text", "plain");
	            request.setContent((null == textMessage ? "" : textMessage), contentTypeHeader);
			}

		}

	}

	public Request makeRequest(MessageFactory messageFactory, SipURI requestURI, String requestMethod,
			CallIdHeader callIdHeader, CSeqHeader cSeqHeader, FromHeader fromHeader, ToHeader toHeader,
			List<ViaHeader> viaHeaders, MaxForwardsHeader maxForwardsHeader) throws ParseException {
		return messageFactory.createRequest(requestURI, requestMethod, callIdHeader, cSeqHeader, fromHeader, toHeader,
				viaHeaders, maxForwardsHeader);
	}

	public FromHeader getFromHeader() {
		return fromHeader;
	}

	public ToHeader getToHeader() {
		return toHeader;
	}

	public SipURI getSipURI() {
		return requestURI;
	}

	public List<ViaHeader> getViaHeaders() {
		return viaHeaders;
	}

	public CallIdHeader getCallIdHeader() {
		return callIdHeader;
	}

	public CSeqHeader getCSeqHeader() {
		return cSeqHeader;
	}

	public MaxForwardsHeader getMaxFowardHeader() {
		return maxFowardsHeader;
	}

	public Request getRequest() {
		return request;
	}

	public String getRequestMethod() {
		return requestMethod;
	}
	
	public void addCustomHeader(HeaderFactory headerFactory, String name, String value, boolean addFirst) throws SipException, ParseException {
		if(null != request && null != headerFactory) {
			if(addFirst) {
				request.addFirst(headerFactory.createHeader(name, value));
			} else {
				request.addHeader(headerFactory.createHeader(name, value));
			}
		}
	}
	
}
