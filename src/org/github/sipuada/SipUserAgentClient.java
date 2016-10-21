package org.github.sipuada;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.Constants.ResponseClass;
import org.github.sipuada.events.CallInvitationAccepted;
import org.github.sipuada.events.CallInvitationCanceled;
import org.github.sipuada.events.CallInvitationDeclined;
import org.github.sipuada.events.CallInvitationFailed;
import org.github.sipuada.events.CallInvitationRinging;
import org.github.sipuada.events.CallInvitationWaiting;
import org.github.sipuada.events.EstablishedCallFailed;
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.MessageNotSent;
import org.github.sipuada.events.MessageSent;
import org.github.sipuada.events.RegistrationFailed;
import org.github.sipuada.events.RegistrationSuccess;
import org.github.sipuada.exceptions.InternalJainSipException;
import org.github.sipuada.exceptions.ResponseDiscarded;
import org.github.sipuada.exceptions.ResponsePostponed;
import org.github.sipuada.exceptions.SipuadaException;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.github.sipuada.plugins.SipuadaPlugin.SessionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import android.gov.nist.gnjvx.sip.Utils;
import android.gov.nist.gnjvx.sip.address.SipUri;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogState;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionDoesNotExistException;
import android.javax.sip.TransactionState;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.AcceptEncodingHeader;
import android.javax.sip.header.AcceptHeader;
import android.javax.sip.header.AllowHeader;
import android.javax.sip.header.AuthorizationHeader;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentEncodingHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.ExtensionHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ProxyAuthenticateHeader;
import android.javax.sip.header.ProxyAuthorizationHeader;
import android.javax.sip.header.ProxyRequireHeader;
import android.javax.sip.header.RAckHeader;
import android.javax.sip.header.RSeqHeader;
import android.javax.sip.header.RequireHeader;
import android.javax.sip.header.RetryAfterHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.UnsupportedHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.header.WWWAuthenticateHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipUserAgentClient {

	private final Logger logger = LoggerFactory.getLogger(SipUserAgentClient.class);

	private final EventBus bus;
	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;
	private final AddressFactory addressMaker;
	private final Map<RequestMethod, SipuadaPlugin> sessionPlugins;

	private final String username;
	private final String primaryHost;
	private final String password;
	private final String localIp;
	private final int localPort;
	private final String transport;
	private final Map<String, Map<String, String>> authNoncesCache = new HashMap<>();
	private final Map<String, Map<String, String>> proxyAuthNoncesCache = new HashMap<>();
	private final Map<String, Map<String, String>> proxyAuthCallIdCache = new HashMap<>();
	private long localCSeq = 0;
	private final List<Address> configuredRouteSet = new LinkedList<>();
	private final Map<URI, Long> registerCSeqs;

	private final URI registerRequestUri;

	public SipUserAgentClient(EventBus eventBus, SipProvider sipProvider,
			Map<RequestMethod, SipuadaPlugin> plugins, MessageFactory messageFactory,
			HeaderFactory headerFactory, AddressFactory addressFactory,
			Map<URI, Long> globalRegisterCSeqs, String... credentialsAndAddress) {
		bus = eventBus;
		provider = sipProvider;
		sessionPlugins = plugins;
		messenger = messageFactory;
		headerMaker = headerFactory;
		addressMaker = addressFactory;
		registerCSeqs = globalRegisterCSeqs;
		username = credentialsAndAddress.length > 0 && credentialsAndAddress[0] != null ?
				credentialsAndAddress[0] : "";
		primaryHost = credentialsAndAddress.length > 1 && credentialsAndAddress[1] != null ?
				credentialsAndAddress[1] : "";
		password = credentialsAndAddress.length > 2 && credentialsAndAddress[2] != null ?
				credentialsAndAddress[2] : "";
		localIp = credentialsAndAddress.length > 3 && credentialsAndAddress[3] != null ?
				credentialsAndAddress[3] : "127.0.0.1";
		localPort = credentialsAndAddress.length > 4 && credentialsAndAddress[4] != null ?
				Integer.parseInt(credentialsAndAddress[4]) : 5060;
		transport = credentialsAndAddress.length > 5 && credentialsAndAddress[5] != null ?
				credentialsAndAddress[5] : "TCP";
		try {
			registerRequestUri = addressMaker.createSipURI(null, primaryHost);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the Request URI for REGISTER requests." +
					"\n[primaryHost] must be a valid domain or IP address, but was: {}.",
					primaryHost, parseException);
			throw new SipuadaException(String
					.format("Invalid host '%s'.", primaryHost), parseException);
		}
	}

	public boolean sendRegisterRequest(CallIdHeader callIdHeader, int expires, String... addresses) {
		if (addresses.length == 0) {
			logger.error("Cannot send a REGISTER request with no bindings.");
			return false;
		}
		synchronized (registerCSeqs) {
			if (!registerCSeqs.containsKey(registerRequestUri)) {
				registerCSeqs.put(registerRequestUri, ++localCSeq);
			}
		}
		long cseq;
		synchronized (registerCSeqs) {
			cseq = registerCSeqs.get(registerRequestUri);
			registerCSeqs.put(registerRequestUri, cseq + 1);
		}
		List<ContactHeader> contactHeaders = new LinkedList<>();
		for (String address : addresses) {
            String addressIp = address.split(":")[0];
			//String addressIp = "150.165.75.133";
			int addressPort = Integer.parseInt(address.split(":")[1]);
			try {
				SipURI contactUri = addressMaker.createSipURI(username, addressIp);
				contactUri.setPort(addressPort);
				contactUri.setTransportParam(transport.toUpperCase());
				contactUri.setParameter("ob", null);
				Address contactAddress = addressMaker.createAddress(contactUri);
				ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
				try {
					contactHeader.setExpires(expires);
				} catch (InvalidArgumentException ignore) {}
				contactHeaders.add(contactHeader);
			} catch (ParseException ignore) {}
		}
		return sendRequest(RequestMethod.REGISTER, username, primaryHost,
				registerRequestUri, callIdHeader, cseq, contactHeaders
				.toArray(new ContactHeader[contactHeaders.size()]));
	}

	//TODO later implement the OPTIONS method.
	//	public void sendOptionsRequest(String remoteUser, String remoteHost) {
	//		sendRequest(RequestMethod.OPTIONS, remoteUser, remoteHost,
	//				...);
	//	}
	//TODO when we do it, make sure that no dialog and session state is
	//messed up with by the flow of incoming responses to this OPTIONS request.

	public boolean sendUnregisterRequest(CallIdHeader callIdHeader, String... expiredAddresses) {
		synchronized (registerCSeqs) {
			if (!registerCSeqs.containsKey(registerRequestUri)) {
				registerCSeqs.put(registerRequestUri, ++localCSeq);
			}
		}
		long cseq;
		synchronized (registerCSeqs) {
			cseq = registerCSeqs.get(registerRequestUri);
			registerCSeqs.put(registerRequestUri, cseq + 1);
		}
		List<Header> additionalHeaders = new LinkedList<>();
		if (expiredAddresses.length == 0) {
			ExpiresHeader expiresHeader;
			try {
				expiresHeader = headerMaker.createExpiresHeader(0);
				additionalHeaders.add(expiresHeader);
			} catch (InvalidArgumentException ignore) {}
			ContactHeader contactHeader = headerMaker.createContactHeader();
			try {
				contactHeader.setExpires(0);
			} catch (InvalidArgumentException ignore) {}
			additionalHeaders.add(contactHeader);
		}
		for (String address : expiredAddresses) {
			String addressIp = address.split(":")[0];
			int addressPort = Integer.parseInt(address.split(":")[1]);
			try {
				SipURI contactUri = addressMaker.createSipURI(username, addressIp);
				contactUri.setPort(addressPort);
				Address contactAddress = addressMaker.createAddress(contactUri);
				ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
				try {
					contactHeader.setExpires(0);
				} catch (InvalidArgumentException ignore) {}
				additionalHeaders.add(contactHeader);
			} catch (ParseException ignore) {}
		}
		return sendRequest(RequestMethod.REGISTER, username, primaryHost,
				registerRequestUri, callIdHeader, cseq, additionalHeaders
				.toArray(new Header[additionalHeaders.size()]));
	}

	public boolean sendInviteRequest(String remoteUser, String remoteHost,
			CallIdHeader callIdHeader) {
		URI requestUri;
		try {
			requestUri = addressMaker.createSipURI(remoteUser, remoteHost);
		} catch (ParseException parseException) {
			logger.error("Could not properly create URI for this INVITE request " +
					"to {} at {}.\n[remoteUser] must be a valid id, [remoteHost] " +
					"must be a valid IP address: {}.", remoteUser, remoteHost,
					parseException.getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}
		long cseq = ++localCSeq;
		List<Header> additionalHeaders = new ArrayList<>();
		SipURI contactUri;
		try {
			contactUri = addressMaker.createSipURI(username, localIp);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the contact URI for {} at {}." +
					"[username] must be a valid id, [localIp] must be a valid " +
					"IP address.", username, localIp, parseException);
			//No need for caller to wait for remote responses.
			return false;
		}
		contactUri.setPort(localPort);
		try {
			contactUri.setTransportParam(transport.toUpperCase());
			contactUri.setParameter("ob", null);
		} catch (ParseException ignore) {}
		Address contactAddress = addressMaker.createAddress(contactUri);
		ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
//		try {
//			contactHeader.setExpires(60);
//		} catch (ParseException ignore) {}
		additionalHeaders.add(contactHeader);
//		try {
//			ExpiresHeader expiresHeader = headerMaker.createExpiresHeader(120);
//			additionalHeaders.add(expiresHeader);
//		} catch (InvalidArgumentException ignore) {}

		for (RequestMethod method : SipUserAgent.ACCEPTED_METHODS) {
			try {
				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
				additionalHeaders.add(allowHeader);
			} catch (ParseException ignore) {}
		}
		try {
			SupportedHeader supportedHeader = headerMaker
				.createSupportedHeader(SessionType.EARLY.getDisposition());
			additionalHeaders.add(supportedHeader);
			supportedHeader = headerMaker.createSupportedHeader("100rel");
			additionalHeaders.add(supportedHeader);
		} catch (ParseException ignore) {}
		return sendRequest(RequestMethod.INVITE, remoteUser, remoteHost, requestUri,
				callIdHeader, cseq, additionalHeaders.toArray(new Header[additionalHeaders.size()]));
	}

	public boolean sendMessageRequest(String remoteUser, String remoteHost, CallIdHeader callIdHeader,
			String content, String contentType, String... additionalHeaders) {
		URI requestUri;
		try {
			requestUri = addressMaker.createSipURI(remoteUser, remoteHost);
		} catch (ParseException parseException) {
			logger.error("Could not properly create URI for this MESSAGE request "
					+ "to {} at {}.\n[remoteUser] must be a valid id, [remoteHost] "
					+ "must be a valid IP address: {}.", remoteUser, remoteHost, parseException.getMessage());
			// No need for caller to wait for remote responses.
			return false;
		}
		long cseq = ++localCSeq;
		String contentTypeValue = contentType.split("/")[0].trim();
		String contentSubTypeValue = contentType.split("/")[1].trim();
		ContentTypeHeader contentTypeHeader;
		try {
			contentTypeHeader = headerMaker
				.createContentTypeHeader(contentTypeValue, contentSubTypeValue);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the ContentTypeHeader for this MESSAGE request "
				+ "to {} at {}.", remoteUser, remoteHost, parseException.getMessage());
			// No need for caller to wait for remote responses.
			return false;
		}
		List<Header> additionalHeadersList = new ArrayList<Header>();
		for (String additionalHeader : additionalHeaders) {
			if (additionalHeader != null) {
				String[] split = additionalHeader.split("\\:");
				String headerName = split[0].trim();
				if (headerName.isEmpty()) {
					continue;
				}
				String headerValue = split.length > 1 ? split[1].trim() : "";
				try {
					Header header = headerMaker.createHeader(headerName, headerValue);
					additionalHeadersList.add(header);
				} catch (ParseException parseException) {
					continue;
				}
			}
		}
		SipURI contactUri;
		try {
			contactUri = addressMaker.createSipURI(username, localIp);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the contact URI for {} at {}."
					+ "[username] must be a valid id, [localIp] must be a valid " + "IP address.",
					username, localIp, parseException);
			// No need for caller to wait for remote responses.
			return false;
		}
		contactUri.setPort(localPort);
		try {
			contactUri.setTransportParam(transport.toUpperCase());
			contactUri.setParameter("ob", null);
		} catch (ParseException ignore) {}
		Address contactAddress = addressMaker.createAddress(contactUri);
		ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
//		try {
//			contactHeader.setExpires(60);
//		} catch (ParseException ignore) {}
		additionalHeadersList.add(contactHeader);
		try {
			ExpiresHeader expiresHeader = headerMaker.createExpiresHeader(120);
			additionalHeadersList.add(expiresHeader);
		} catch (InvalidArgumentException ignore) {}
		for (RequestMethod method : SipUserAgent.ACCEPTED_METHODS) {
			try {
				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
				additionalHeadersList.add(allowHeader);
			} catch (ParseException ignore) {}
		}
		return sendRequest(RequestMethod.MESSAGE, remoteUser, remoteHost, requestUri, callIdHeader, cseq, content,
			contentTypeHeader, additionalHeadersList.toArray(new Header[additionalHeadersList.size()]));
	}

	private boolean sendRequest(RequestMethod method, String remoteUser,
			String remoteHost, URI requestUri, CallIdHeader callIdHeader, long cseq,
			Header... additionalHeaders) {
		return sendRequest(method, remoteUser, remoteHost, requestUri,
			callIdHeader, cseq, null, null, additionalHeaders);
	}

	private boolean sendRequest(RequestMethod method, String remoteUser,
			String remoteHost, URI requestUri, CallIdHeader callIdHeader, long cseq,
			String content, ContentTypeHeader contentTypeHeader, Header... additionalHeaders) {
		try {
			URI addresserUri = addressMaker.createSipURI(username, primaryHost);
			URI addresseeUri = addressMaker.createSipURI(remoteUser, remoteHost);
			return sendRequest(method, requestUri, addresserUri, addresseeUri, null,
					callIdHeader, cseq, content, contentTypeHeader, additionalHeaders);
		} catch (ParseException parseException) {
			logger.error("Could not properly create addresser and addressee URIs for " +
					"this {} request from {} at {} to {} at {}." +
					"\n[username] and [remoteUser] must be valid ids; " +
					"[localHost] and [remoteHost], valid domains or IP addresses: {}.",
					method, username, primaryHost, remoteUser, remoteHost,
					parseException.getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}
	}

	public boolean sendReinviteRequest(Dialog dialog) {
		//TODO everything related to sending RE-INVITE requests, such as
		//making sure they are sent only when they should (it MUST NOT be sent
		//while another INVITE transaction is in progress in either direction within the
		//context of this dialog).
		//TODO also it's important to make sure that failures to this RE-INVITE
		//don't cause the current dialog and session to cease to exist, unless the received
		//response is either a 481 (Call Does Not Exist) or a 408 (Request Timeout), in
		//which case the dialog and session shall be terminated.
		//This will probably need a review of almost all of this source code.
		//TODO finally, also make sure this RE-INVITE is handled properly in the case
		//a 491 response is received (given that the transaction layer doesn't already
		//do this transparently for us, that is).
		return sendRequest(RequestMethod.INVITE, dialog);
	}

	public boolean sendByeRequest(Dialog dialog) {
		return sendByeRequest(dialog, false, null);
	}

	public boolean sendByeRequest(Dialog dialog, boolean shouldReportCallFailed, String reason) {
		List<Header> additionalHeaders = new LinkedList<>();
		if (shouldReportCallFailed) {
			try {
				additionalHeaders.add(headerMaker
						.createHeader(SipUserAgent.X_FAILURE_REASON_HEADER, reason));
			} catch (ParseException ignore) {}
		}
		return sendRequest(RequestMethod.BYE, dialog,
				additionalHeaders.toArray(new Header[additionalHeaders.size()]));
	}

	public boolean sendMessageRequest(Dialog dialog, String content, String contentType, String... additionalHeaders) {
		String contentTypeValue = contentType.split("/")[0].trim();
		String contentSubTypeValue = contentType.split("/")[1].trim();
		ContentTypeHeader contentTypeHeader;
		try {
			contentTypeHeader = headerMaker
				.createContentTypeHeader(contentTypeValue, contentSubTypeValue);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the ContentTypeHeader for this MESSAGE request which was "
				+ "meant to be sent in context of call {{}}.", dialog.getCallId().getCallId(), parseException.getMessage());
			// No need for caller to wait for remote responses.
			return false;
		}
		List<Header> additionalHeadersList = new ArrayList<Header>();
		for (String additionalHeader : additionalHeaders) {
			if (additionalHeader != null) {
				String[] split = additionalHeader.split("\\:");
				String headerName = split[0].trim();
				if (headerName.isEmpty()) {
					continue;
				}
				String headerValue = split.length > 1 ? split[1].trim() : "";
				try {
					Header header = headerMaker.createHeader(headerName, headerValue);
					additionalHeadersList.add(header);
				} catch (ParseException parseException) {
					continue;
				}
			}
		}
		return sendRequest(RequestMethod.MESSAGE, dialog, content, contentTypeHeader,
			additionalHeadersList.toArray(new Header[additionalHeadersList.size()]));
	}

	private boolean sendPrackRequest(final Dialog dialog, Request request, Response response) {
		final String method = RequestMethod.PRACK.toString();
		List<Header> additionalHeaders = new LinkedList<>();
		RSeqHeader rSeqHeader = (RSeqHeader) response.getHeader(RSeqHeader.NAME);
		CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
		RAckHeader rAckHeader;
		try {
			rAckHeader = headerMaker.createRAckHeader
				(rSeqHeader.getSequenceNumber(), (int) cSeqHeader.getSeqNumber(),
					cSeqHeader.getMethod());
			additionalHeaders.add(rAckHeader);
		} catch (InvalidArgumentException ignore) {
		} catch (ParseException ignore) {}
		URI addresseeUri = dialog.getRemoteParty().getURI();
		URI requestUri = (URI) addresseeUri.clone();
		CallIdHeader callIdHeader = dialog.getCallId();
		long cseq = dialog.getLocalSeqNumber();
		if (cseq == 0) {
			cseq = localCSeq + 1;
		}
		if (localCSeq < cseq) {
			localCSeq = cseq;
		}
		Address from = addressMaker.createAddress(dialog.getLocalParty().getURI());
		String fromTag = dialog.getLocalTag();
		Address to = addressMaker.createAddress(dialog.getRemoteParty().getURI());
		String toTag = dialog.getRemoteTag();
		List<Address> canonRouteSet = new LinkedList<>();
		Address remoteTarget = dialog.getRemoteTarget();
		if (remoteTarget == null) {
			ContactHeader contactHeader = (ContactHeader) response
				.getHeader(ContactHeader.NAME);
			if (contactHeader != null) {
				remoteTarget = contactHeader.getAddress();
			} else {
				remoteTarget = dialog.getRemoteParty();
			}
		}
		final URI remoteTargetUri = remoteTarget.getURI();
		Iterator<?> routeHeaders = dialog.getRouteSet();
		while (routeHeaders.hasNext()) {
			RouteHeader routeHeader = (RouteHeader) routeHeaders.next();
			canonRouteSet.add(routeHeader.getAddress());
		}
		List<Address> normalizedRouteSet = new LinkedList<>();
		if (!canonRouteSet.isEmpty()) {
			if (((SipURI)canonRouteSet.get(0).getURI()).hasLrParam()) {
				requestUri = remoteTargetUri;
				for (Address address : canonRouteSet) {
					normalizedRouteSet.add(address);
				}
			}
			else {
				requestUri = canonRouteSet.get(0).getURI();
				for (int i=1; i<canonRouteSet.size(); i++) {
					normalizedRouteSet.add(canonRouteSet.get(i));
				}
				Address remoteTargetAddress = addressMaker.createAddress(remoteTargetUri);
				normalizedRouteSet.add(remoteTargetAddress);
			}
		}
		else {
			requestUri = (URI) remoteTargetUri.clone();
		}
		ViaHeader viaHeader = null;
		try {
			viaHeader = headerMaker.createViaHeader(localIp, localPort, transport, null);
			//viaHeader.setRPort(); // Don't allow rport as 'rport='. Must be 'rport' or 'rport=15324' for example. Use rport only for UDP.
			FromHeader fromHeader = headerMaker.createFromHeader(from, fromTag);
//			fromHeader.setParameter("transport", "tcp");
			ToHeader toHeader = headerMaker.createToHeader(to, toTag);
//			toHeader.setParameter("transport", "tcp");
			final Request prackRequest = messenger.createRequest(requestUri, method.toString(),
					callIdHeader, headerMaker.createCSeqHeader(cseq, method.toString()),
					fromHeader, toHeader, Collections.singletonList(viaHeader),
					headerMaker.createMaxForwardsHeader(70));
			if (!normalizedRouteSet.isEmpty()) {
				for (Address routeAddress : normalizedRouteSet) {
					RouteHeader routeHeader = headerMaker.createRouteHeader(routeAddress);
					prackRequest.addHeader(routeHeader);
				}
			}
			else {
				prackRequest.removeHeader(RouteHeader.NAME);
			}
			for (int i=0; i<additionalHeaders.size(); i++) {
				prackRequest.addHeader(additionalHeaders.get(i));
			}
			final ClientTransaction clientTransaction = provider
				.getNewClientTransaction(prackRequest);
			viaHeader.setBranch(clientTransaction.getBranchId());
			final String callId = callIdHeader.getCallId();
			if (putAnswerIntoAckRequestIfApplicable(RequestMethod.INVITE,
				callId, SessionType.EARLY, request, response, prackRequest)) {
				logger.info("UAC could provide suitable answer to {} offer, so, "
					+ "sending {} to {} response to {} request (from {}:{})...",
					SessionType.EARLY, RequestMethod.PRACK, response.getStatusCode(),
					request.getMethod(), localIp, localPort);
					logger.debug("Request Dump:\n{}\n", prackRequest);
			} else {
				//No need for caller to wait for remote responses.
				return false;
			}
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						if (doSendRequest(prackRequest, clientTransaction, dialog)) {
							logger.info("{} request sent by {}:{} through {}.", method,
									localIp, localPort, transport);
						}
						else {
							logger.error("Could not send this {} request.", method);
							reportRequestError(callId, clientTransaction,
									"Request could not be parsed or contained invalid state.");
						}
					} catch (SipException requestCouldNotBeSent) {
						logger.error("Could not send this {} request: {} ({}).",
								method, requestCouldNotBeSent.getMessage(),
								requestCouldNotBeSent.getCause().getMessage());
						reportRequestError(callId, clientTransaction,
								"Request could not be sent: " + String.format("%s (%s).",
										requestCouldNotBeSent.getMessage(),
										requestCouldNotBeSent.getCause().getMessage()));
					}
				}

			}).start();
			return true;
		} catch (ParseException requestCouldNotBeBuilt) {
			logger.error("Could not properly create mandatory headers for " +
					"this {} request.\nVia: [localIp: {}, localPort: {}, " +
					"transport: {}, remotePort: {}]: {}.", method, viaHeader.getHost(),
					viaHeader.getPort(), viaHeader.getTransport(), viaHeader.getRPort(),
					requestCouldNotBeBuilt.getMessage());
		} catch (TransactionUnavailableException requestCouldNotBeBuilt) {
			logger.error("Could not properly create client transaction to handle" +
					" this {} request: {}.", method, requestCouldNotBeBuilt.getMessage());
		} catch (InvalidArgumentException requestCouldNotBeBuilt) {
			logger.error("Could not properly create mandatory headers for " +
					"this {} request.\nVia: [localIp: {}, localPort: {}, " +
					"transport: {}, remotePort: {}]: {}.", method, viaHeader.getHost(),
					viaHeader.getPort(), viaHeader.getTransport(), viaHeader.getRPort(),
					requestCouldNotBeBuilt.getMessage());
		}
		//No need for caller to wait for remote responses.
		return false;
	}

	private boolean sendRequest(RequestMethod method, Dialog dialog, Header... additionalHeaders) {
		return sendRequest(method, dialog, null, null, additionalHeaders);
	}

	private boolean sendRequest(RequestMethod method, Dialog dialog,
			String content, ContentTypeHeader contentTypeHeader, Header... additionalHeaders) {
		URI addresserUri = dialog.getLocalParty().getURI();
		URI addresseeUri = dialog.getRemoteParty().getURI();
		URI requestUri = (URI) addresseeUri.clone();
		CallIdHeader callIdHeader = dialog.getCallId();
		long cseq = dialog.getLocalSeqNumber();
		if (cseq == 0) {
			cseq = localCSeq + 1;
		}
		if (localCSeq < cseq) {
			localCSeq = cseq;
		}
		return sendRequest(method, requestUri, addresserUri, addresseeUri, dialog,
			callIdHeader, cseq, content, contentTypeHeader, additionalHeaders);
	}

//	private boolean sendRequest(final RequestMethod method, URI requestUri,
//			URI addresserUri, URI addresseeUri, final Dialog dialog,
//			final CallIdHeader callIdHeader, long cseq, Header... additionalHeaders) {
//		return sendRequest(method, requestUri, addresserUri, addresseeUri, dialog,
//			callIdHeader, cseq, null, null, additionalHeaders);
//	}

	private boolean sendRequest(final RequestMethod method, URI requestUri,
			URI addresserUri, URI addresseeUri, final Dialog dialog,
			final CallIdHeader callIdHeader, long cseq, String content,
			ContentTypeHeader contentTypeHeader, Header... additionalHeaders) {
		if (method == RequestMethod.CANCEL || method == RequestMethod.ACK
				|| method == RequestMethod.UNKNOWN) {
			//This method is meant for the INVITE request and
			//the following NON-INVITE requests: REGISTER, OPTIONS and BYE.
			//(In the future, INFO and MESSAGE as well.)
			logger.debug("[sendRequest(RequestMethod, URI, URI, URI, Dialog, CallIdHeader," +
					"long, Header...)] method forbidden for {} requests.", method);
			//No need for caller to wait for remote responses.
			return false;
		}
		Address from, to;
		String fromTag, toTag;
		List<Address> canonRouteSet = new LinkedList<>();
		final URI remoteTargetUri;
		if (dialog != null) {
			remoteTargetUri = dialog.getRemoteTarget().getURI();
			from = addressMaker.createAddress(dialog.getLocalParty().getURI());
			fromTag = dialog.getLocalTag();
			to = addressMaker.createAddress(dialog.getRemoteParty().getURI());
			toTag = dialog.getRemoteTag();

			Iterator<?> routeHeaders = dialog.getRouteSet();
			while (routeHeaders.hasNext()) {
				RouteHeader routeHeader = (RouteHeader) routeHeaders.next();
				canonRouteSet.add(routeHeader.getAddress());
			}
		}
		else {
			remoteTargetUri = (URI) requestUri.clone();
			from = addressMaker.createAddress(addresserUri);
			fromTag = Utils.getInstance().generateTag();
			to = addressMaker.createAddress(addresseeUri);
			toTag = null;
			
			canonRouteSet.addAll(configuredRouteSet);
		}
		List<Address> normalizedRouteSet = new LinkedList<>();
		if (!canonRouteSet.isEmpty()) {
			if (((SipURI)canonRouteSet.get(0).getURI()).hasLrParam()) {
				requestUri = remoteTargetUri;
				for (Address address : canonRouteSet) {
					normalizedRouteSet.add(address);
				}
			}
			else {
				requestUri = canonRouteSet.get(0).getURI();
				for (int i=1; i<canonRouteSet.size(); i++) {
					normalizedRouteSet.add(canonRouteSet.get(i));
				}
				Address remoteTargetAddress = addressMaker.createAddress(remoteTargetUri);
				normalizedRouteSet.add(remoteTargetAddress);
			}
		}
		else {
			requestUri = (URI) remoteTargetUri.clone();
		}
		ViaHeader viaHeader = null;
		try {
			viaHeader = headerMaker.createViaHeader(localIp, localPort, transport, null);
			//viaHeader.setRPort(); // Don't allow rport as 'rport='. Must be 'rport' or 'rport=15324' for example. Use rport only for UDP.
			FromHeader fromHeader = headerMaker.createFromHeader(from, fromTag);
//			fromHeader.setParameter("transport", "tcp");
			ToHeader toHeader = headerMaker.createToHeader(to, toTag);
//			toHeader.setParameter("transport", "tcp");
			final Request request = messenger.createRequest(requestUri, method.toString(),
					callIdHeader, headerMaker.createCSeqHeader(cseq, method.toString()),
					fromHeader, toHeader, Collections.singletonList(viaHeader),
					headerMaker.createMaxForwardsHeader(70));
			if (!normalizedRouteSet.isEmpty()) {
				for (Address routeAddress : normalizedRouteSet) {
					RouteHeader routeHeader = headerMaker.createRouteHeader(routeAddress);
					request.addHeader(routeHeader);
				}
			}
			else {
				request.removeHeader(RouteHeader.NAME);
			}
			for (int i=0; i<additionalHeaders.length; i++) {
				request.addHeader(additionalHeaders[i]);
			}
			final ClientTransaction clientTransaction = provider
					.getNewClientTransaction(request);
			viaHeader.setBranch(clientTransaction.getBranchId());
			final String callId = callIdHeader.getCallId();
			//TODO maybe inject answer into PRACK here?!
			if (isDialogCreatingRequest(method)) {
				putOfferIntoRequestIfApplicable(method, callId, SessionType.REGULAR, request);
			} else if (isPayloadSenderRequest(method, content, contentTypeHeader)) {
				request.setContent(content, contentTypeHeader);
			}
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						if (doSendRequest(request, clientTransaction, dialog)) {
							logger.info("{} request sent by {}:{} through {}.", method,
									localIp, localPort, transport);
						}
						else {
							logger.error("Could not send this {} request.", method);
							reportRequestError(callId, clientTransaction,
									"Request could not be parsed or contained invalid state.");
						}
					} catch (SipException requestCouldNotBeSent) {
						logger.error("Could not send this {} request: {} ({}).",
								method, requestCouldNotBeSent.getMessage(),
								requestCouldNotBeSent.getCause().getMessage());
						reportRequestError(callId, clientTransaction,
								"Request could not be sent: " + String.format("%s (%s).",
										requestCouldNotBeSent.getMessage(),
										requestCouldNotBeSent.getCause().getMessage()));
					}
				}

			}).start();
			return true;
		} catch (ParseException requestCouldNotBeBuilt) {
			logger.error("Could not properly create mandatory headers for " +
					"this {} request.\nVia: [localIp: {}, localPort: {}, " +
					"transport: {}, remotePort: {}]: {}.", method, viaHeader.getHost(),
					viaHeader.getPort(), viaHeader.getTransport(), viaHeader.getRPort(),
					requestCouldNotBeBuilt.getMessage());
		} catch (TransactionUnavailableException requestCouldNotBeBuilt) {
			logger.error("Could not properly create client transaction to handle" +
					" this {} request: {}.", method, requestCouldNotBeBuilt.getMessage());
		} catch (InvalidArgumentException requestCouldNotBeBuilt) {
			logger.error("Could not properly create mandatory headers for " +
					"this {} request.\nVia: [localIp: {}, localPort: {}, " +
					"transport: {}, remotePort: {}]: {}.", method, viaHeader.getHost(),
					viaHeader.getPort(), viaHeader.getTransport(), viaHeader.getRPort(),
					requestCouldNotBeBuilt.getMessage());
		}
		//No need for caller to wait for remote responses.
		return false;
	}

	private boolean isDialogCreatingRequest(RequestMethod method) {
		switch (method) {
			case INVITE:
			case OPTIONS:
				return true;
			default:
				return false;
		}
	}

	private boolean isPayloadSenderRequest(RequestMethod method, String content,
			ContentTypeHeader contentTypeHeader) {
		switch (method) {
			case INVITE:
			case ACK:
			case PRACK:
			case MESSAGE:
			case INFO:
				return content != null && contentTypeHeader != null;
			default:
				return false;
		}
	}

	private void putOfferIntoRequestIfApplicable(RequestMethod method,
			String callId, SessionType type, Request request) {
		SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
		if (sessionPlugin == null) {
			logger.info("No plug-in available to generate {} offer to be "
				+ "inserted into {} request.", type, method);
			return;
		}
		SessionDescription offer = null;
		try {
			offer = sessionPlugin.generateOffer(callId, type, method, localIp);
		} catch (Throwable unexpectedException) {
			logger.error("Bad plug-in crashed while trying to generate {} offer " +
				"to be inserted into {} request.", type, method, unexpectedException);
			return;
		}
		if (offer == null) {
			logger.error("Plug-in {} generated no {} offer to be inserted into {} request.",
				sessionPlugin.getClass().getName(), type, method);
			return;
		}
		try {
			request.setContent(offer, headerMaker
				.createContentTypeHeader("application", "sdp"));
			request.setContentDisposition(headerMaker
				.createContentDispositionHeader(type.getDisposition()));
		} catch (ParseException parseException) {
			logger.error("Plug-in-generated {} offer {{}} by {} "
				+ "could not be inserted into {} request.", type, offer.toString(),
				sessionPlugin.getClass().getName(), method, parseException);
			return;
		}
		logger.info("Plug-in-generated {} offer {{}} by {} inserted into {} request.",
			type, offer.toString(), sessionPlugin.getClass().getName(), method);
	}

	public boolean sendCancelRequest(final ClientTransaction clientTransaction) {
		if (!clientTransaction.getRequest().getMethod()
				.equals(RequestMethod.INVITE.toString())) {
			//This method is meant for canceling INVITE requests only.
			logger.debug("[sendCancelRequest(clientTransaction)] method forbidden for " +
					"{} requests.", clientTransaction.getRequest().getMethod());
			//No need for caller to wait for remote responses.
			return false;
		}
		final Request cancelRequest;
		try {
			cancelRequest = clientTransaction.createCancel();
		} catch (SipException requestCouldNotBeBuilt) {
			//Could not properly build a CANCEL request - this shouldn't happen.
			logger.debug("Could not properly create {} request: {} ({}).",
					RequestMethod.CANCEL, requestCouldNotBeBuilt.getMessage(),
					requestCouldNotBeBuilt.getCause().getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}
		final Timer timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				if (clientTransaction.getState() == null) {
					return;
				}
				switch (clientTransaction.getState().getValue()) {
					case TransactionState._PROCEEDING:
						try {
							if (doSendRequest(cancelRequest, null, null)) {
								logger.debug("{} request sent.", RequestMethod.CANCEL);
							}
							else {
								logger.error("Could not send this {} request.",
										RequestMethod.CANCEL);
							}
						} catch (SipException requestCouldNotBeSent) {
							logger.error("Could not send this {} request: {} ({}).",
									RequestMethod.CANCEL,
									requestCouldNotBeSent.getMessage(),
									requestCouldNotBeSent.getCause().getMessage());
						}
						timer.cancel();
						break;
					case TransactionState._COMPLETED:
					case TransactionState._TERMINATED:
						timer.cancel();
						sendByeRequest(clientTransaction.getDialog());
				}
			}
		}, 180, 180);
		//Caller must expect remote responses.
		return true;
	}

	private void reportRequestError(String callId,
			ClientTransaction clientTransaction, String errorMessage) {
		switch (Constants.getRequestMethod(clientTransaction.getRequest().getMethod())) {
			case REGISTER:
				bus.post(new RegistrationFailed(errorMessage, callId));
				break;
			case INVITE:
				bus.post(new CallInvitationFailed(errorMessage, callId));
				break;
			case MESSAGE:
				bus.post(new MessageNotSent(errorMessage, callId));
				break;
			default:
				break;
		}
	}

	protected void processResponse(ResponseEvent responseEvent) {
		ClientTransaction clientTransaction = responseEvent.getClientTransaction();
		if (clientTransaction != null) {
			RequestMethod method = RequestMethod.UNKNOWN;
			Request request = clientTransaction.getRequest();
			if (request != null) {
				try {
					method = RequestMethod.valueOf(request.getMethod());
				} catch (IllegalArgumentException ignore) {}
			}
			Response response = responseEvent.getResponse();
			int statusCode = response.getStatusCode();
			logger.debug("Response arrived to UAC for {} request with code {}.",
					method, statusCode);
			handleResponse(statusCode, response, clientTransaction);
		}
		else {
			logger.debug("Response arrived but no request made by this UAC is associated " +
					"with it, so it's considered stray and discarded.");
		}
	}

	protected void processTimeout(TimeoutEvent timeoutEvent) {
		if (!timeoutEvent.isServerTransaction()) {
			ClientTransaction clientTransaction = timeoutEvent.getClientTransaction();
			logger.debug("Timeout arrived to UAC - translated into response with code {}.",
					Response.REQUEST_TIMEOUT);
			handleResponse(Response.REQUEST_TIMEOUT, null, clientTransaction);
		}
	}

	protected void processFatalTransportError(IOExceptionEvent exceptionEvent) {
		logger.debug("Fatal transport error occurred - translated into response " +
				"with code {}.", Response.SERVICE_UNAVAILABLE);
		Object source = exceptionEvent.getSource();
		String callIdInAdvance = null;
		if (source instanceof Dialog) {
			callIdInAdvance = ((Dialog) source).getCallId().getCallId();
		}
		else if (source instanceof ClientTransaction) {
			ClientTransaction clientTransaction = ((ClientTransaction) source);
			if (clientTransaction.getDialog() != null) {
				callIdInAdvance = clientTransaction
						.getDialog().getCallId().getCallId();
			}
		}
		handleResponse(Response.SERVICE_UNAVAILABLE, null, null, callIdInAdvance);
	}
	
	private void handleResponse(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		handleResponse(statusCode, response, clientTransaction, null);
	}

	private void handleResponse(int statusCode, Response response,
			ClientTransaction clientTransaction, String callIdInAdvance) {
		try {
			if (tryHandlingResponseGenerically(statusCode, response, clientTransaction)) {
				if (response == null || clientTransaction == null) {
					logger.debug("Response translated from Timeout or Fatal transport " +
							"error couldn't be handled generically, so it's discarded.");
					throw new ResponseDiscarded();
				}
				switch (Constants.getRequestMethod(clientTransaction
						.getRequest().getMethod())) {
					case REGISTER:
						handleRegisterResponse(statusCode, response, clientTransaction);
						break;
					case INVITE:
						handleInviteResponse(statusCode, response, clientTransaction);
						break;
					case BYE:
						handleByeResponse(statusCode, response, clientTransaction);
						break;
					case MESSAGE:
						handleMessageResponse(statusCode, response, clientTransaction);
						break;
					case UNKNOWN:
					default:
						break;
				}
			}
			else {
				reportResponseError(statusCode, response,
						clientTransaction, callIdInAdvance);
			}
		} catch (ResponseDiscarded requestDiscarded) {
		} catch (ResponsePostponed requestPostponed) {}
	}

	private boolean tryHandlingResponseGenerically(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		logger.debug("Attempting to handle response {}.", statusCode);
		logger.debug("Response Dump: {{}}", response);
		if (response != null) {
			ListIterator<?> iterator = response.getHeaders(ViaHeader.NAME);
			int viaHeaderCount = 0;
			while (iterator != null && iterator.hasNext()) {
				iterator.next();
				viaHeaderCount++;
			}
			if (viaHeaderCount > 1) {
				logger.debug("Response is corrupted because it contains multiple " +
						"Via headers, so it's discarded.");
				//No method-specific handling is required.
				throw new ResponseDiscarded();
			}
		}
		switch (statusCode) {
			case Response.PROXY_AUTHENTICATION_REQUIRED:
			case Response.UNAUTHORIZED:
				logger.debug("Performing necessary authorization procedures.");
				handleAuthorizationRequired(response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.REQUEST_ENTITY_TOO_LARGE:
				//TODO handle this by retrying omitting the body or using one of
				//smaller length.
				//If the condition is temporary, the server SHOULD include a
				//Retry-After header field to indicate that it is temporary and after
				//what time the client MAY try again.
				//For now just checking whether simple rescheduling is applicable.
				handleUnsupportedExtension(response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.UNSUPPORTED_MEDIA_TYPE:
				logger.debug("Performing necessary media types negotiation.");
				//TODO missing: filtering any media types without languages listed in
				//the Accept-Language in the response.
				handleUnsupportedMediaTypes(response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.BAD_EXTENSION:
				logger.debug("Performing necessary extensions negotiation.");
				handleUnsupportedExtension(response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.NOT_FOUND:
			case Response.BUSY_HERE:
			case Response.BUSY_EVERYWHERE:
			case Response.DECLINE:
			case Response.TEMPORARILY_UNAVAILABLE:
				logger.debug("Callee is busy or not found at the moment." +
						"\nWill attempt a retry if it is allowed at a later time.");
				handleByReschedulingIfApplicable(response, clientTransaction, false);
				//No method-specific handling is required.
				return false;
			case Response.SERVER_INTERNAL_ERROR:
			case Response.SERVICE_UNAVAILABLE:
				logger.debug("Attempt to reach callee failed due to errors " +
						"or service unavailability. \nWill attempt a retry if it is " +
						"allowed at a later time.");
				handleByReschedulingIfApplicable(response, clientTransaction, false);
				//No method-specific handling is required.
				return false;
			case Response.REQUEST_TIMEOUT:
			case Response.SERVER_TIMEOUT:
				//TODO handle this by retrying the same request after a while if it is
				//outside of a dialog, otherwise consider the dialog and session terminated.
				logger.debug("Attempt to reach callee timed out." +
						"\nWill attempt a retry if it is allowed at a later time.");
				handleByReschedulingIfApplicable(response, clientTransaction, true);
				//No method-specific handling is required.
				return false;
			/*
			 * case Response.ADDRESS_INCOMPLETE:
			 */
				//TODO figure out how to handle this by doing overlapped dialing(?)
				//until the response no longer is a 484 (Address Incomplete).
				//No method-specific handling is required.
				//return false;
			/*
			 * case Response.UNSUPPORTED_URI_SCHEME: 
			 */
				//TODO handle this by retrying, this time using a SIP(S) URI.
				//No method-specific handling is required.
				//return false;
			/*
			 * case Response.AMBIGUOUS:
			 */
				//FIXME I think no method-specific handling is required.
				//TODO figure out how to handle this by prompting for user intervention
				//for deciding which of the choices provided is to be used in the retry.
				//return false;
			case Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST:
				handleByTerminatingIfWithinDialog(clientTransaction);
				return false;
			case Response.REQUEST_TERMINATED:
				return handleThisRequestTerminated(clientTransaction);
		}
		switch (Constants.getResponseClass(statusCode)) {
			case PROVISIONAL:
				//TODO give the application layer feedback on this event.
				return true;
			case SUCCESS:
				return true;
			case REDIRECT:
				//TODO perform redirect request(s) transparently.
				//TODO remember to, if no redirect is sent, remove any early dialogs
				//associated with this request and tell the application layer about it.
				//FIXME for now we are always doing the task above for obvious reasons.
				//TODO also remember to implement the AMBIGUOUS case above as it's similar.
				return false;
			case CLIENT_ERROR:
			case SERVER_ERROR:
			case GLOBAL_ERROR:
				//TODO remove any early dialogs associated with this request
				//and tell the application layer about it.
				return false;
			case UNKNOWN:
				//Handle this by simply discarding this unknown response.
				throw new ResponseDiscarded();
		}
		return true;
	}

	private void reportResponseError(int statusCode, Response response,
			ClientTransaction clientTransaction, String callIdInAdvance) {
		String reasonPhrase = response != null ? response.getReasonPhrase() :
			clientTransaction == null ? "Fatal error" : "Time out";
		logger.info("{} response arrived: {}.", statusCode, reasonPhrase);
		String codeAndReason = String.format("Following response arrived: %d (%s).",
				statusCode, reasonPhrase);
		if (clientTransaction == null) {
			logger.error("A Fatal {} error occurred.{}", statusCode,
					callIdInAdvance != null ? "" : " If it was NOT during processing of" +
							" a REGISTER request, no end-user callback might get fired.");
			//Just in case this error is associated with a REGISTER or INVITE request,
			//a RegistrationFailed event and a CallInvitationFailed event are sent.");
			if (callIdInAdvance != null) {
				bus.post(new RegistrationFailed(codeAndReason, callIdInAdvance));
				bus.post(new CallInvitationFailed(codeAndReason, callIdInAdvance));
			}
			return;
		}
		Request request = clientTransaction.getRequest();
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		switch (Constants.getRequestMethod(request.getMethod())) {
			case REGISTER:
				bus.post(new RegistrationFailed(codeAndReason, callId));
				break;
			case INVITE:
				if (statusCode == Response.BUSY_HERE ||
					statusCode == Response.BUSY_EVERYWHERE ||
					statusCode == Response.TEMPORARILY_UNAVAILABLE) {
					bus.post(new CallInvitationDeclined(codeAndReason, callId));
				}
				else {
					bus.post(new CallInvitationFailed(codeAndReason, callId));
				}
				break;
			case CANCEL:
				bus.post(new CallInvitationFailed(codeAndReason, callId));
				break;
			case BYE:
				ExtensionHeader extensionHeader = (ExtensionHeader) request
				    .getHeader(SipUserAgent.X_FAILURE_REASON_HEADER);
				if (extensionHeader != null) {
					String reason = extensionHeader.getValue();
					if (reason != null) {
						bus.post(new EstablishedCallFailed(reason, callId));
						break;
					}
				}
				bus.post(new EstablishedCallFinished(callId));
				break;
			default:
				break;
		}
	}

	private void handleAuthorizationRequired(Response response,
			ClientTransaction clientTransaction) {
		Request request = createFromRequest(clientTransaction.getRequest());
		incrementCSeq(request);
		SipUri hostUri = new SipUri();
		try {
			hostUri.setHost(primaryHost);
		} catch (ParseException parseException) {
			logger.error("Could not create the host URI for {}: {}.",
					primaryHost, parseException.getMessage());
			return;
		}
		String username = this.username, password = this.password;
		if (username.trim().isEmpty()) {
			username = "anonymous";
			password = "";
		}
		ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
		String toHeaderValue = toHeader.getAddress().getURI().toString();
		Map<String, String> possiblyFailedAuthRealms = new HashMap<>();
		ListIterator<?> usedAuthHeaders = request
				.getHeaders(AuthorizationHeader.NAME);
		while (usedAuthHeaders != null && usedAuthHeaders.hasNext()) {
			AuthorizationHeader authHeader = (AuthorizationHeader)
					usedAuthHeaders.next();
			possiblyFailedAuthRealms.put(authHeader.getRealm(),
					authHeader.getNonce());
		}
		Map<String, String> possiblyFailedProxyAuthRealms = new HashMap<>();
		ListIterator<?> usedProxyAuthHeaders = request
				.getHeaders(ProxyAuthorizationHeader.NAME);
		while (usedProxyAuthHeaders != null && usedProxyAuthHeaders.hasNext()) {
			ProxyAuthorizationHeader authHeader = (ProxyAuthorizationHeader)
					usedProxyAuthHeaders.next();
			possiblyFailedProxyAuthRealms.put(authHeader.getRealm(),
					authHeader.getNonce());
		}
		boolean worthAuthenticating = false;
		ListIterator<?> wwwAuthenticateHeaders = response
				.getHeaders(WWWAuthenticateHeader.NAME);
		while (wwwAuthenticateHeaders != null && wwwAuthenticateHeaders.hasNext()) {
			WWWAuthenticateHeader wwwAuthenticateHeader =
					(WWWAuthenticateHeader) wwwAuthenticateHeaders.next();
			String realm = wwwAuthenticateHeader.getRealm();
			String nonce = wwwAuthenticateHeader.getNonce();
			worthAuthenticating = addAuthorizationHeader(request, hostUri,
					toHeaderValue, username, password, realm, nonce);
			if (!authNoncesCache.containsKey(toHeaderValue)) {
				authNoncesCache.put(toHeaderValue, new HashMap<String, String>());
			}
			authNoncesCache.get(toHeaderValue).put(realm, nonce);
		}
		ListIterator<?> proxyAuthenticateHeaders = response
				.getHeaders(ProxyAuthenticateHeader.NAME);
		while (proxyAuthenticateHeaders != null && proxyAuthenticateHeaders.hasNext()) {
			ProxyAuthenticateHeader proxyAuthenticateHeader =
					(ProxyAuthenticateHeader) proxyAuthenticateHeaders.next();
			String realm = proxyAuthenticateHeader.getRealm();
			String nonce = proxyAuthenticateHeader.getNonce();
			worthAuthenticating = addProxyAuthorizationHeader(request, hostUri,
					toHeaderValue, username, password, realm, nonce);
			if (!proxyAuthNoncesCache.containsKey(toHeaderValue)) {
				proxyAuthNoncesCache.put(toHeaderValue, new HashMap<String, String>());
				proxyAuthCallIdCache.put(toHeaderValue, new HashMap<String, String>());
			}
			proxyAuthNoncesCache.get(toHeaderValue).put(realm, nonce);
			CallIdHeader callIdHeader = (CallIdHeader) request
					.getHeader(CallIdHeader.NAME);
			String callId = callIdHeader.getCallId();
			proxyAuthCallIdCache.get(toHeaderValue).put(realm, callId);
		}
		int attempt = (Integer) clientTransaction.getApplicationData();
		if (attempt >= 3) {
			worthAuthenticating = false;
		}
		if (worthAuthenticating) {
			try {
				if (doSendRequest(request, null, clientTransaction.getDialog(), false, ++attempt)) {
					logger.info("{} request sent (with auth credentials).",
							request.getMethod());
					throw new ResponsePostponed();
				}
				else {
					//Request that would authenticate could not be sent.
					logger.error("Could not resend this {} request with authentication " +
							"credentials.", request.getMethod());
				}
			} catch (SipException requestCouldNotBeSent) {
				//Request that would authenticate could not be sent.
				logger.error("Could not resend this {} request with authentication " +
						"credentials: {}.", request.getMethod(),
						requestCouldNotBeSent.getMessage(), requestCouldNotBeSent.getCause());
			}
		}
		else {
			//Not worth authenticating because server already denied
			//this exact request or no appropriate auth headers could be added.
			logger.error("Credentials for {} request were denied, or no proper auth " +
					"headers were passed along with the 401/407 response from the UAS.",
					request.getMethod());
		}
	}

	private void handleUnsupportedMediaTypes(Response response,
			ClientTransaction clientTransaction) {
		Request request = createFromRequest(clientTransaction.getRequest());
		incrementCSeq(request);

		List<String> acceptedEncodings = new LinkedList<>();
		ListIterator<?> acceptEncodingHeaders =
				response.getHeaders(AcceptEncodingHeader.NAME);
		if (acceptEncodingHeaders != null && acceptEncodingHeaders != null) {
			while (acceptEncodingHeaders.hasNext()) {
				AcceptEncodingHeader acceptEncodingHeader =
						(AcceptEncodingHeader) acceptEncodingHeaders.next();
				StringBuilder encoding =
						new StringBuilder(acceptEncodingHeader.getEncoding());
				float qValue = acceptEncodingHeader.getQValue();
				if (qValue != -1) {
					encoding.insert(0, String.format("q=%.2f, ", qValue));
				}
				acceptedEncodings.add(encoding.toString());
			}
		}
		StringBuilder overlappingEncodings = new StringBuilder();
		ContentEncodingHeader contentEncodingHeader =
				request.getContentEncoding();
		if (contentEncodingHeader != null) {
			String definedEncoding = contentEncodingHeader.getEncoding();
			for (String acceptedEncoding : acceptedEncodings) {
				String encodingWithoutQValue = acceptedEncoding;
				if (encodingWithoutQValue.contains(",")) {
					encodingWithoutQValue = acceptedEncoding
							.split(",")[1].trim();
				}
				if (definedEncoding.contains(encodingWithoutQValue)) {
					if (overlappingEncodings.length() != 0) {
						overlappingEncodings.append("; ");
					}
					overlappingEncodings.append(acceptedEncoding);
				}
			}
		}
		boolean overlappingEncodingsFound = false;
		if (overlappingEncodings.length() > 0) {
			try {
				ContentEncodingHeader newContentEncodingHeader = headerMaker
						.createContentEncodingHeader(overlappingEncodings.toString());
				request.setContentEncoding(newContentEncodingHeader);
				overlappingEncodingsFound = true;
			} catch (ParseException ignore) {}
		}
		
		boolean shouldBypassContentTypesCheck = false;
		Map<String, String> typeSubtypeToQValue = new HashMap<>();
		Map<String, Set<String>> typeToSubTypes = new HashMap<>();
		ListIterator<?> acceptHeaders = response.getHeaders(AcceptHeader.NAME);
		while (acceptHeaders != null && acceptHeaders.hasNext()) {
			AcceptHeader acceptHeader =
					(AcceptHeader) acceptHeaders.next();
			String contentType = acceptHeader.getContentType();
			String contentSubType = acceptHeader.getContentSubType();
			if (acceptHeader.allowsAllContentTypes()) {
				shouldBypassContentTypesCheck = true;
				break;
			}
			else if (acceptHeader.allowsAllContentSubTypes()) {
				typeToSubTypes.put(contentType, new HashSet<String>());
			}
			else {
				if (!typeToSubTypes.containsKey(contentType)) {
					typeToSubTypes.put(contentType, new HashSet<String>());
				}
				typeToSubTypes.get(contentType).add(contentSubType);
			}
			float qValue = acceptHeader.getQValue();
			if (qValue != -1) {
				typeSubtypeToQValue.put(String.format("%s/%s", contentType,
						contentSubType), Float.toString(qValue));
			}
		}
		boolean overlappingContentTypesFound = false;
		if (!shouldBypassContentTypesCheck) {
			List<ContentTypeHeader> overlappingContentTypes
					= new LinkedList<>();
			ListIterator<?> definedContentTypeHeaders =
					request.getHeaders(ContentTypeHeader.NAME);
			while (definedContentTypeHeaders != null
					&& definedContentTypeHeaders.hasNext()) {
				ContentTypeHeader contentTypeHeader = (ContentTypeHeader)
					definedContentTypeHeaders.next();
				boolean addThisContentType = false;
				String contentType = contentTypeHeader
						.getContentType();
				String contentSubType = contentTypeHeader
						.getContentSubType();
				if (typeToSubTypes.containsKey(contentType)) {
					Set<String> acceptedSubTypes =
							typeToSubTypes.get(contentType);
					if (acceptedSubTypes.isEmpty()) {
						addThisContentType = true;
					}
					else {
						for (String acceptedSubType : acceptedSubTypes) {
							if (acceptedSubType.equals(contentSubType)) {
								addThisContentType = true;
								break;
							}
						}
					}
				}
				if (addThisContentType) {
					String typeSubtype = String.format("%s/%s",
							contentType, contentSubType);
					if (typeSubtypeToQValue.containsKey(typeSubtype)) {
						String qValue = typeSubtypeToQValue
								.get(typeSubtype);
						try {
							contentTypeHeader.setParameter("q", qValue);
						} catch (ParseException ignore) {}
					}
					overlappingContentTypes.add(contentTypeHeader);
				}
			}
			if (overlappingContentTypes.size() > 0) {
				for (ContentTypeHeader header
						: overlappingContentTypes) {
					request.addHeader(header);
				}
				overlappingContentTypesFound = true;
			}
		}
		if (overlappingEncodingsFound && overlappingContentTypesFound) {
			try {
				if (doSendRequest(request, null, clientTransaction.getDialog())) {
					logger.info("{} request failed because it contained media types " +
							"unsupported by the UAS. This UAC resent the request including" +
							" only supported media types", request.getMethod());
					throw new ResponsePostponed();
				}
				else {
					//Request that would amend this situation could not be sent.
					logger.error("{} request failed because it contained media types" +
							" unsupported by the UAS.\nCould not resend this request with" +
							" supported media types.", request.getMethod());
				}
			} catch (SipException requestCouldNotBeSent) {
				//Request that would amend this situation could not be sent.
				logger.error("{} request failed because it contained media types" +
						" unsupported by the UAS.\nCould not resend this request with" +
						" supported media types: {}.", request.getMethod(),
						requestCouldNotBeSent.getMessage());
			}
		}
		else {
			//Cannot satisfy the media type requirements since this UAC doesn't
			//support any that are accepted by the UAS that sent this response.
			logger.error("{} request failed because it contained media types unsupported " +
					"by the UAS.\nThis UAC cannot satisfy these media type requirements.",
					request.getMethod());
		}
	}

	private void handleUnsupportedExtension(Response response,
			ClientTransaction clientTransaction) {
		Request request = createFromRequest(clientTransaction.getRequest());
		incrementCSeq(request);
		
		UnsupportedHeader unsupportedHeader =
				(UnsupportedHeader) response.getHeader(UnsupportedHeader.NAME);
		if (unsupportedHeader == null) {
			logger.info("No Unsupported header present in response, so UAC cannot " +
					"satisfy this request.");
			logger.error("{} request failed due to requiring some extensions unsupported" +
					" by the UAS.\nUAC could not send new request amending this situation" +
					" since response contained no Unsupported header.", request.getMethod());
			return;
		}
		String unsupportedOptionTags = unsupportedHeader.getOptionTag();

		RequireHeader requireHeader =
				(RequireHeader) request.getHeader(RequireHeader.NAME);
		ProxyRequireHeader proxyRequireHeader =
				(ProxyRequireHeader) request.getHeader(ProxyRequireHeader.NAME);
		StringBuilder allowedOptionTags = new StringBuilder();
		if (requireHeader != null) {
			String[] requiredOptionTags = requireHeader.getOptionTag().split(",");
			for (String requiredOptionTag : requiredOptionTags) {
				requiredOptionTag = requiredOptionTag.trim();
				if (!unsupportedOptionTags.contains(requiredOptionTag)) {
					if (allowedOptionTags.length() > 0) {
						allowedOptionTags.append(", ");
					}
					allowedOptionTags.append(requiredOptionTag);
				}
			}
			if (allowedOptionTags.length() > 0) {
				try {
					requireHeader.setOptionTag(allowedOptionTags.toString());
				} catch (ParseException ignore) {}
				request.setHeader(requireHeader);
			}
			else {
				request.removeHeader(RequireHeader.NAME);
			}
		}
		else if (proxyRequireHeader != null) {
			String[] proxyRequiredOptionTags = proxyRequireHeader.getOptionTag().split(",");
			for (String proxyRequiredOptionTag : proxyRequiredOptionTags) {
				proxyRequiredOptionTag = proxyRequiredOptionTag.trim();
				if (!unsupportedOptionTags.contains(proxyRequiredOptionTag)) {
					if (allowedOptionTags.length() > 0) {
						allowedOptionTags.append(", ");
					}
					allowedOptionTags.append(proxyRequiredOptionTag);
				}
			}
			if (allowedOptionTags.length() > 0) {
				try {
					proxyRequireHeader.setOptionTag(allowedOptionTags.toString());
				} catch (ParseException ignore) {}
				request.setHeader(proxyRequireHeader);
			}
			else {
				request.removeHeader(ProxyRequireHeader.NAME);
			}
		}
		try {
			if (doSendRequest(request, null, clientTransaction.getDialog())) {
				logger.info("{} request failed due to requiring some extensions" +
						" unsupported the UAS.\nUAC just resent this request with" +
						" supported extensions.", request.getMethod());
				throw new ResponsePostponed();
			}
			else {
				//Request that would amend this situation could not be sent.
				logger.error("{} request failed due to requiring some extensions" +
						" unsupported by the UAS.\nUAC could not send new request" +
						" amending this situation.", request.getMethod());
			}
		} catch (SipException requestCouldNotBeSent) {
			//Request that would amend this situation could not be sent.
			logger.error("{} request failed due to requiring some extensions unsupported" +
					" by the UAS.\nUAC could not send new request " +
					"amending this situation: {} ({}).", request.getMethod(),
					requestCouldNotBeSent.getMessage(),
					requestCouldNotBeSent.getCause().getMessage());
		}
	}

	private void handleByReschedulingIfApplicable(final Response response,
			final ClientTransaction clientTransaction, boolean isTimeout) {
		if (isTimeout) {
			boolean shouldSendBye = true;
			if (response == null) {
				shouldSendBye = !handleThisRequestTerminated(clientTransaction);
			}
			Dialog dialog = clientTransaction.getDialog();
			if (dialog != null && !(dialog.getState() == DialogState.EARLY ||
					dialog.getState() == DialogState.TERMINATED) && shouldSendBye) {
				sendByeRequest(dialog);
			}
			return;
		}
		else {
			if (clientTransaction == null) {
				//This means a Fatal IO error occurred. Simply return error back to
				//application layer in this case, as there's nothing this UAC can do.
				return;
			}
			if (response == null) {
				logger.error("[handleByReschedulingIfApplicable] received NULL " +
						"Non-Timeout response - this should never happen.");
				return;
			}
		}

		final Request request = createFromRequest(clientTransaction.getRequest());
		incrementCSeq(request);

		RetryAfterHeader retryAfterHeader =
				(RetryAfterHeader) response.getHeader(RetryAfterHeader.NAME);
		if (retryAfterHeader != null) {
			final int retryAfterSeconds = retryAfterHeader.getRetryAfter();
			int durationSeconds = retryAfterHeader.getDuration();
			if (durationSeconds == 0 || durationSeconds > 300) {
				Timer timer = new Timer();
				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						try {
							if (doSendRequest(request, null,
									clientTransaction.getDialog())) {
								logger.info("{} request resent after {} seconds.",
										request.getMethod(), retryAfterSeconds);
							}
							else {
								//Could not reschedule request.
								logger.error("Could not resend {} request " +
									"after {} seconds.", request.getMethod(),
									retryAfterSeconds);
								reportResponseError(response.getStatusCode(), response,
										clientTransaction, null);
							}
						} catch (SipException requestCouldNotBeSent) {
							//Could not reschedule request.
							logger.error("Could not resend {} request after {} seconds: {} ({}).",
									request.getMethod(), retryAfterSeconds,
									requestCouldNotBeSent.getMessage(),
									requestCouldNotBeSent.getCause().getMessage());
							reportResponseError(response.getStatusCode(), response,
									clientTransaction, null);
						}
					}

				}, retryAfterSeconds * 1000);
				logger.info("{} request failed or timed out.\nUAC will resend " +
						"in {} seconds.", request.getMethod(), retryAfterSeconds);
				throw new ResponsePostponed();
			}
		}
		//Request is not allowed to reschedule or it would be pointless to do so
		//because availability frame is too short.
		logger.error("{} request failed or timed out. UAC is not allowed to resend.",
				request.getMethod());
	}

	private void handleByTerminatingIfWithinDialog(ClientTransaction clientTransaction) {
		logger.debug("{} request failed because call or transaction did not exist.",
				clientTransaction.getRequest().getMethod());
		Dialog dialog = clientTransaction.getDialog();
		if (dialog != null && !(dialog.getState() == DialogState.EARLY ||
				dialog.getState() == DialogState.TERMINATED)) {
			sendByeRequest(dialog, true, "There's no call associated to this request.");
		}
	}

	private void handleRegisterResponse(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		Request request = clientTransaction.getRequest();
		String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			logger.info("{} response to REGISTER arrived.", statusCode);
			bus.post(new RegistrationSuccess(callId, response.getHeaders(ContactHeader.NAME)));
		}
	}

	private void handleInviteResponse(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		Request request = clientTransaction.getRequest();
		String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		Dialog dialog = clientTransaction.getDialog();
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			if (dialog != null) {
				try {
					CSeqHeader cseqHeader = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
					Request ackRequest = dialog.createAck(cseqHeader.getSeqNumber());
					boolean sendByeRightAway = false;
					if (!putAnswerIntoAckRequestIfApplicable(RequestMethod.INVITE,
							callId, SessionType.REGULAR, request, response, ackRequest)) {
						sendByeRightAway = true;
					}
					logger.info("Sending {} to {} response to {} request (from {}:{})...",
						RequestMethod.ACK, response.getStatusCode(), request.getMethod(),
						localIp, localPort);
					logger.debug("Request Dump:\n{}\n", ackRequest);
					try {
						dialog.sendAck(ackRequest);
					} catch (RuntimeException lowLevelStackFailed) {
						logger.error("{} to {} response to {} request could not be sent " +
							"due to a JAINSIP-level failure.", RequestMethod.ACK,
							response.getStatusCode(), request.getMethod(),
							lowLevelStackFailed);
						throw new InternalJainSipException("Severe JAINSIP-level failure!",
							lowLevelStackFailed);
					}
					logger.info("{} response to {} arrived, so {} sent.", statusCode,
						RequestMethod.INVITE, RequestMethod.ACK);
					logger.info("New call established: {}.", callId);
					if (sendByeRightAway) {
						sendByeRequest(dialog, true, "Media types negotiation failed.");
					}
					bus.post(new CallInvitationAccepted(callId, dialog));
				} catch (InvalidArgumentException ignore) {
				} catch (SipException ignore) {}
			} else {
				logger.error("Could not process {} response to {} request: dialog is missing!" +
					response.getStatusCode(), request.getMethod());
				throw new InternalJainSipException("Cannot process successful response "
					+ "to INVITE: dialog is missing!", null);
			}
		}
		else if (ResponseClass.PROVISIONAL == Constants.getResponseClass(statusCode)) {
			if (statusCode == Response.RINGING) {
				logger.info("Ringing!");
				bus.post(new CallInvitationRinging(callId, clientTransaction));
			} else if (statusCode == Response.SESSION_PROGRESS) {
				if (dialog != null) {
					boolean earlyMediaIsSupported = false;
					@SuppressWarnings("unchecked")
					ListIterator<Header> supportedHeaders = response.getHeaders(SupportedHeader.NAME);
					while (supportedHeaders != null && supportedHeaders.hasNext()) {
						SupportedHeader supportedHeader = (SupportedHeader) supportedHeaders.next();
						if (supportedHeader.getOptionTag().toLowerCase()
								.contains(SessionType.EARLY.getDisposition())) {
							earlyMediaIsSupported = true;
							break;
						}
					}
					if (earlyMediaIsSupported) {
						if (sendPrackRequest(dialog, request, response)) {
							logger.info("{} response to {} arrived, so {} sent.", statusCode,
								RequestMethod.INVITE, RequestMethod.PRACK);
							logger.info("Early media session established: {}.", callId);
//								bus.post(new CallInvitationRinging(callId, clientTransaction, true));
							//FIXME post event which represents establishment of an early media session!
						} else {
							logger.error("{} response to {} arrived, but UAC cannot provide suitable "
								+ "answer to {} offer or PRACK could not be sent, so treating "
								+ "response as 180 and aborting early media session negotiation attempt.",
								statusCode, request.getMethod(), SessionType.EARLY);
							bus.post(new CallInvitationRinging(callId, clientTransaction));
						}
					} else {
						logger.error("{} response to {} arrived, but remote UAS hasn't indicated "
							+ " support for {{}} negotiations, so treating response as 180 and aborting "
							+ "early media session negotiation attempt.", statusCode,
							request.getMethod(), SessionType.EARLY.getDisposition());
						bus.post(new CallInvitationRinging(callId, clientTransaction));
					}
				} else {
					logger.error("Could not process {} response to {} request: dialog is missing!" +
						response.getStatusCode(), request.getMethod());
					throw new InternalJainSipException("Cannot process successful response "
						+ "to INVITE: dialog is missing!", null);
				}
			} else {
				bus.post(new CallInvitationWaiting(callId, clientTransaction));
			}
			logger.info("{} response arrived.", statusCode);
		}
	}

	private boolean putAnswerIntoAckRequestIfApplicable(RequestMethod method, String callId,
			SessionType type, Request request, Response response, Request ackRequest) {
		if (request.getContent() != null && request.getContentDisposition()
				.getDispositionType().toLowerCase().trim().equals(type.getDisposition())) {
			boolean responseArrivedWithNoAnswer = response.getContent() == null
				|| !response.getContentDisposition().getDispositionType()
					.toLowerCase().trim().equals(type.getDisposition());
			if (responseArrivedWithNoAnswer) {
				logger.error("{} request was sent with an {} offer but {} response " +
					"arrived with no {} answer so this UAC will terminate the dialog right away.",
					method, type, response.getStatusCode(), type);
			}
			else {
				try {
					SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
					if (sessionPlugin != null) {
						SessionDescription answer = SdpFactory.getInstance()
							.createSessionDescriptionFromString(new String(response.getRawContent()));
						try {
							sessionPlugin.receiveAnswerToAcceptedOffer(callId, type, answer);
						} catch (Throwable unexpectedException) {
							logger.error("Bad plug-in crashed while receiving {} answer " +
								"that arrived alongside {} response to {} request. The UAC will terminate " +
								"the dialog right away.", type, response.getStatusCode(),
								method, unexpectedException);
							return false;
						}
					}
				} catch (SdpParseException parseException) {
					logger.error("{} answer arrived in {} response to {} request, but could not be properly" +
						" parsed, so it was discarded. The UAC will terminate the dialog right away.",
						type, response.getStatusCode(), method, parseException);
					return false;
				}
			}
			return !responseArrivedWithNoAnswer;
		}
		if (response.getContent() == null || !response.getContentDisposition().getDispositionType()
				.toLowerCase().trim().equals(type.getDisposition())) {
			logger.info("No {} offer/answer exchange performed in this transaction.", type);
			return true;
		}
		SessionDescription offer;
		try {
			offer = SdpFactory.getInstance()
				.createSessionDescriptionFromString(new String(response.getRawContent()));
		} catch (SdpParseException parseException) {
			logger.error("{} offer arrived in {} response to {} request, but could not be properly parsed, " +
				"so it was discarded.", type, response.getStatusCode(), method, parseException);
			return false;
		}
		SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
		if (sessionPlugin == null) {
			logger.info("No plug-in available to generate valid {} answer to {} offer {} in {} response " +
				"to {} request.", type, type, offer.toString(), response.getStatusCode(), method);
			return false;
		}
		SessionDescription answer = null;
		try {
			answer = sessionPlugin.generateAnswer(callId, type, method, offer, localIp);
		} catch (Throwable unexpectedException) {
			logger.error("Bad plug-in crashed while trying to generate {} answer " +
				"to be inserted into {} for {} response to {} request. The UAC will terminate the dialog " +
				"right away.", type, ackRequest.getMethod(), response.getStatusCode(), method, unexpectedException);
			return false;
		}
		if (answer == null) {
			logger.error("Plug-in {} could not generate valid {} answer to {} offer {} in {} response " +
				"to {} request.", sessionPlugin.getClass().getName(), type, type, offer.toString(),
				response.getStatusCode(), method);
			return false;
		}
		try {
			ackRequest.setContent(answer, headerMaker.createContentTypeHeader("application", "sdp"));
			ackRequest.setContentDisposition(headerMaker.createContentDispositionHeader(type.getDisposition()));
		} catch (ParseException parseException) {
			logger.error("Plug-in-generated {} answer {{}} to {} offer {{}} by {} could not be inserted into {} " +
				"for {} response to {} request.", type, answer.toString(), type, offer.toString(),
				sessionPlugin.getClass().getName(), ackRequest.getMethod(), response.getStatusCode(),
				method, parseException);
			return false;
		}
		logger.info("Plug-in-generated {} answer {{}} to {} offer {{}} by {} inserted into {} for {} response" +
			" to {} request.", type, answer.toString(), type, offer.toString(), sessionPlugin.getClass().getName(),
			ackRequest.getMethod(), response.getStatusCode(), method);
		return true;
	}

	private void handleByeResponse(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			logger.info("{} response to BYE arrived.", statusCode);
			handleThisRequestTerminated(clientTransaction);
			String callId = null; Dialog dialog = clientTransaction.getDialog();
			if (dialog != null) {
				callId = dialog.getCallId().getCallId();
			}
			Request request = clientTransaction.getRequest();
			if (request != null) {
				ExtensionHeader extensionHeader = (ExtensionHeader) request
						.getHeader(SipUserAgent.X_FAILURE_REASON_HEADER);
				if (extensionHeader != null) {
					String reason = extensionHeader.getValue();
					if (reason != null) {
						bus.post(new EstablishedCallFailed(reason, callId));
						return;
					}
				}
			}
			bus.post(new EstablishedCallFinished(callId));
		}
	}

	private boolean handleThisRequestTerminated(ClientTransaction clientTransaction) {
		Request request = clientTransaction.getRequest();
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		switch (Constants.getRequestMethod(request.getMethod())) {
			case INVITE:
				//This could mean that a CANCEL succeeded in canceling the
				//original INVITE request.
				logger.info("If a CANCEL was issued, it succeded in canceling " +
						"a call invitation.");
				bus.post(new CallInvitationCanceled("Caller canceled " +
						"outgoing INVITE.", callId, false));
				return true;
			case BYE:
				//This means a BYE succeeded in terminating a INVITE request within a Dialog.
				logger.info("BYE succeded in terminating a call.");
				return true;
			default:
				//This should never happen.
				return false;
		}
	}

	private Request createFromRequest(Request original) {
		Request derived = (Request) original.clone();
		ListIterator<?> iterator = original.getHeaders(ViaHeader.NAME);
		String newBranchId = Utils.getInstance().generateBranchId();
		derived.removeHeader(ViaHeader.NAME);
		while (iterator != null && iterator.hasNext()) {
			ViaHeader viaHeader = (ViaHeader) iterator.next();
			if (viaHeader.getBranch() != null) {
				try {
					viaHeader.setBranch(newBranchId);
				} catch (ParseException ignore) {}
			}
			derived.addHeader(viaHeader);
		}
		return derived;
	}

	private void incrementCSeq(Request request) {
		CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
		try {
			long newCSeq = cseq.getSeqNumber() + 1;
			if (request.getMethod().equals(RequestMethod.REGISTER.toString())) {
				synchronized (registerCSeqs) {
					newCSeq = registerCSeqs.get(registerRequestUri);
					registerCSeqs.put(registerRequestUri, newCSeq + 1);
				}
			}
			cseq.setSeqNumber(newCSeq);
			if (localCSeq < newCSeq) {
				localCSeq = newCSeq;
			}
		} catch (InvalidArgumentException ignore) {}
		request.setHeader(cseq);
	}

	private void handleMessageResponse(int statusCode, Response response, ClientTransaction clientTransaction) {
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			logger.info("{} response to MESSAGE arrived.", statusCode);
			CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
			final String callId = (callIdHeader).getCallId();
			bus.post(new MessageSent(callId));
		}
	}

	private boolean doSendRequest(Request request,
			ClientTransaction clientTransaction, Dialog dialog)
			throws TransactionUnavailableException, SipException {
		//TODO EH ESSE
		return doSendRequest(request, clientTransaction, dialog, true);
	}

	private boolean doSendRequest(Request request, ClientTransaction clientTransaction,
			Dialog dialog, boolean tryAddingAuthorizationHeaders)
					throws TransactionUnavailableException, SipException {
		return doSendRequest(request, clientTransaction, dialog, tryAddingAuthorizationHeaders, 1);
	}

	private boolean doSendRequest(Request request, ClientTransaction clientTransaction,
			Dialog dialog, boolean tryAddingAuthorizationHeaders, int attempt)
			throws TransactionUnavailableException, SipException {
		ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
		String toHeaderValue = toHeader.getAddress().getURI().toString();

		SipUri hostUri = new SipUri();
		try {
			hostUri.setHost(primaryHost);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the host URI for this {} request." +
					"\nMust be a valid domain or IP address: {}.", request.getMethod(),
					primaryHost, parseException.getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}

		if (tryAddingAuthorizationHeaders) {
			logger.debug("About to try adding authorization headers before sending " +
					"this {} request.", request.getMethod());
			if (authNoncesCache.containsKey(toHeaderValue)) {
				Map<String, String> probableRealms = new HashMap<>();
				for (Entry<String, String> entry :
					authNoncesCache.get(toHeaderValue).entrySet()) {
					String realm = entry.getKey();
					String remoteHost = toHeaderValue.split("@")[1];
					if (realm.contains(remoteHost)) {
						String nonce = entry.getValue();
						probableRealms.put(realm, nonce);
					}
				}
				for (String realm : probableRealms.keySet()) {
					addAuthorizationHeader(request, hostUri, toHeaderValue,
							username, password, realm, probableRealms.get(realm));
				}
			}
			if (proxyAuthNoncesCache.containsKey(toHeaderValue)) {
				Map<String, String> probableRealms = new HashMap<>();
				for (Entry<String, String> entry :
					proxyAuthNoncesCache.get(toHeaderValue).entrySet()) {
					String realm = entry.getKey();
					String remoteHost = toHeaderValue.split("@")[1];
					if (realm.contains(remoteHost)) {
						String nonce = entry.getValue();
						probableRealms.put(realm, nonce);
					}
				}
				for (String realm : probableRealms.keySet()) {
					CallIdHeader callIdHeader = (CallIdHeader) request
							.getHeader(CallIdHeader.NAME);
					String thisCallId = callIdHeader.getCallId();
					String savedCallId = proxyAuthCallIdCache.get(toHeaderValue).get(realm);
					if (thisCallId.equals(savedCallId)) {
						addProxyAuthorizationHeader(request, hostUri, toHeaderValue,
								username, password, realm, probableRealms.get(realm));
					}
				}
			}
		}

		ClientTransaction newClientTransaction = clientTransaction;
		if (clientTransaction == null) {
			newClientTransaction = provider.getNewClientTransaction(request);
			ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
			try {
				viaHeader.setBranch(newClientTransaction.getBranchId());
//				viaHeader.setRPort();
			} catch (ParseException ignore) {}
//			} catch (InvalidArgumentException ignore) {}
		}
		newClientTransaction.setApplicationData(attempt);
		if (dialog != null) {
			try {
				logger.info("Sending {} request within a dialog (from {}:{})...", request.getMethod(),
						localIp, localPort);
				logger.debug("Request Dump:\n{}\n", request);
				try {
					dialog.sendRequest(newClientTransaction);
				} catch (RuntimeException lowLevelStackFailed) {
					logger.error("{} request within a dialog could not be sent due to a " +
							"JAINSIP-level failure.", request.getMethod(),
							lowLevelStackFailed);
					throw new InternalJainSipException("Severe JAINSIP-level failure!",
							lowLevelStackFailed);
				}
				//Caller must expect remote responses.
				return true;
			}
			catch (TransactionDoesNotExistException invalidTransaction) {
				//A invalid (probably null) client transaction
				//can't be used to send this request.
				logger.debug("{} request could not be sent: {}.",
						request.getMethod(), invalidTransaction.getMessage());
				//No need for caller to wait for remote responses.
				return false;
			}
		}
		else {
			logger.info("Sending {} request (from {}:{})...", request.getMethod(),
					localIp, localPort);
			logger.debug("Request Dump:\n{}\n", request);
			try {
				newClientTransaction.sendRequest();
			} catch (RuntimeException lowLevelStackFailed) {
				logger.error("{} request could not be sent due to a " +
						"JAINSIP-level failure.", request.getMethod(),
						lowLevelStackFailed);
				throw new InternalJainSipException("Severe JAINSIP-level failure!",
						lowLevelStackFailed);
			}
			//Caller must expect remote responses.
			return true;
		}
	}

	private boolean addAuthorizationHeader(Request request, URI hostUri,
			String toHeaderValue, String username, String password,
			String realm, String nonce) {
		String responseDigest = AuthorizationDigest.getDigest(username, realm,
				password, request.getMethod(), hostUri.toString(), nonce);
		AuthorizationHeader authorizationHeader;
		try {
			authorizationHeader = headerMaker
					.createAuthorizationHeader("Digest");
			authorizationHeader.setAlgorithm("MD5");
			authorizationHeader.setURI(hostUri);
			authorizationHeader.setUsername(username);
			authorizationHeader.setRealm(realm);
			authorizationHeader.setNonce(nonce);
			authorizationHeader.setResponse(responseDigest);
		} catch (ParseException parseException) {
			logger.warn("Authorization header could not be added to authenticate " +
					"a {} request: {}.", request.getMethod(), parseException.getMessage());
			//Authorization header could not be added.
			return false;
		}
		request.removeHeader(AuthorizationHeader.NAME);
		request.addHeader(authorizationHeader);
		//Authorization header could be added.
		return true;
	}

	private boolean addProxyAuthorizationHeader(Request request, URI hostUri,
			String toHeaderValue, String username, String password,
			String realm, String nonce) {
		String responseDigest = AuthorizationDigest.getDigest(username, realm,
				password, request.getMethod(), hostUri.toString(), nonce);
		ProxyAuthorizationHeader proxyAuthorizationHeader;
		try {
			proxyAuthorizationHeader = headerMaker
					.createProxyAuthorizationHeader("Digest");
			proxyAuthorizationHeader.setAlgorithm("MD5");
			if (hostUri != null) {
				proxyAuthorizationHeader.setURI(hostUri);
			}
			proxyAuthorizationHeader.setUsername(username);
			proxyAuthorizationHeader.setRealm(realm);
			proxyAuthorizationHeader.setNonce(nonce);
			proxyAuthorizationHeader.setResponse(responseDigest);
		} catch (ParseException parseException) {
			logger.warn("ProxyAuthorization header could not be added to authenticate " +
					"a {} request: {}.", request.getMethod(), parseException.getMessage());
			//ProxyAuthorization header could not be added.
			return false;
		}
		request.removeHeader(ProxyAuthorizationHeader.NAME);
		request.addHeader(proxyAuthorizationHeader);
		//ProxyAuthorization header could be added.
		return true;
	}

}

