package org.github.sipuada.messages;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.github.sipuada.SipRequestState;

import android.javax.sip.InvalidArgumentException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.SipProvider;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;

public class Message {

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

	// Request
	private Request request;

	public static FromHeader createFromHeader(AddressFactory addressFactory, HeaderFactory headerFactory,
			String username, String sipDomain, String displayName, String tag)
					throws PeerUnavailableException, ParseException {
		SipURI fromAddress = addressFactory.createSipURI(username, sipDomain);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(displayName);
		return headerFactory.createFromHeader(fromNameAddress, tag);
	}

	public ToHeader createToHeader(AddressFactory addressFactory, HeaderFactory headerFactory, String username,
			String sipDomain, String displayName) throws PeerUnavailableException, ParseException {
		SipURI fromAddress = addressFactory.createSipURI(username, sipDomain);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(displayName);
		return headerFactory.createToHeader(fromNameAddress, null);
	}

	/**
	 * Constructor
	 * 
	 * @param state
	 * @param fromHeader
	 * @param toHeader
	 * @throws ParseException
	 * @throws PeerUnavailableException
	 * @throws InvalidArgumentException
	 */
	public Message(SipRequestState state, final String fromUsername, final String fromSipDomain,
			final String fromDisplayName, final String fromTag, final String localIpAddress, final int localSipPort,
			final String transport, final String toUsername, final String toSipDomain, final String toDisplayName,
			AddressFactory addressFactory, HeaderFactory headerFactory, MessageFactory messageFactory, SipProvider sipProvider, String requestMethod, Long callSequence, Integer maxForwards)
					throws PeerUnavailableException, ParseException, InvalidArgumentException {

		// create To and From headers
		this.fromHeader = createFromHeader(addressFactory, headerFactory, fromUsername, fromSipDomain, fromDisplayName,
				fromTag);
		this.toHeader = createToHeader(addressFactory, headerFactory, toUsername, toSipDomain, toDisplayName);

		// create a new Request URI
		requestURI = addressFactory.createSipURI(fromUsername, fromSipDomain);

		// add Via header
		ViaHeader viaHeader = headerFactory.createViaHeader(localIpAddress, localSipPort, transport, null);
		viaHeader.setRPort();
		viaHeaders.add(viaHeader);

		// create a new CallId header
		callIdHeader = sipProvider.getNewCallId();

		// create a new Cseq header
		cSeqHeader = headerFactory.createCSeqHeader((null == callSequence ? 1L : callSequence), requestMethod);

		// create a new MaxForwards Header
		maxFowardsHeader = headerFactory.createMaxForwardsHeader((null == maxForwards ? 70 : maxForwards));
		
		// create the request
		request = makeRequest(messageFactory, requestURI, requestMethod, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxFowardsHeader);

	}
	
	public Request makeRequest(MessageFactory messageFactory, SipURI requestURI, String requestMethod, CallIdHeader callIdHeader, CSeqHeader cSeqHeader, FromHeader fromHeader, ToHeader toHeader, List<ViaHeader> viaHeaders, MaxForwardsHeader maxForwardsHeader) throws ParseException {
		return messageFactory.createRequest(requestURI, requestMethod, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
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

	

}
