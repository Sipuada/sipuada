package org.github.sipuada;

import java.text.ParseException;
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
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.RegistrationFailed;
import org.github.sipuada.events.RegistrationSuccess;
import org.github.sipuada.exceptions.ResponseDiscarded;
import org.github.sipuada.exceptions.ResponsePostponed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import android.gov.nist.gnjvx.sip.Utils;
import android.gov.nist.gnjvx.sip.address.SipUri;
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
import android.javax.sip.header.AuthorizationHeader;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentEncodingHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ProxyAuthenticateHeader;
import android.javax.sip.header.ProxyAuthorizationHeader;
import android.javax.sip.header.ProxyRequireHeader;
import android.javax.sip.header.RequireHeader;
import android.javax.sip.header.RetryAfterHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.UnsupportedHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.header.WWWAuthenticateHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class UserAgentClient {

	private final Logger logger = LoggerFactory.getLogger(UserAgentClient.class);

	private final EventBus bus;
	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;
	private final AddressFactory addressMaker;

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
	private Map<URI, CallIdHeader> registerCallIds = new HashMap<>();
	private Map<URI, Long> registerCSeqs = new HashMap<>();

	public UserAgentClient(EventBus eventBus, SipProvider sipProvider,
			MessageFactory messageFactory, HeaderFactory headerFactory,
			AddressFactory addressFactory, String... credentialsAndAddress) {
		bus = eventBus;
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
		addressMaker = addressFactory;
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
	}

	public boolean sendRegisterRequest() {
		URI requestUri;
		try {
			requestUri = addressMaker.createSipURI(null, primaryHost);
		} catch (ParseException parseException) {
			logger.error("Could not properly create URI for this REGISTER request to {}." +
					"\nMust be a valid domain or IP address: {}.", primaryHost,
					parseException.getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}
		if (!registerCallIds.containsKey(requestUri)) {
			registerCallIds.put(requestUri, provider.getNewCallId());
			registerCSeqs.put(requestUri, ++localCSeq);
		}
		CallIdHeader callIdHeader = registerCallIds.get(requestUri);
		long cseq = registerCSeqs.get(requestUri);
		registerCSeqs.put(requestUri, cseq + 1);
		
		List<ContactHeader> contactHeaders = new LinkedList<>();
		SipURI contactUri;
		try {
			contactUri = addressMaker.createSipURI(username, localIp);
			contactUri.setPort(localPort);
			Address contactAddress = addressMaker.createAddress(contactUri);
			ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
			try {
				contactHeader.setExpires(3600);
			} catch (InvalidArgumentException ignore) {}
			contactHeaders.add(contactHeader);
		} catch (ParseException ignore) {}

		//TODO *IF* request is a REGISTER, keep in mind that:
		/*
		 * UAs MUST NOT send a new registration (that is, containing new Contact
		 * header field values, as opposed to a retransmission) until they have
		 * received a final response from the registrar for the previous one or
		 * the previous REGISTER request has timed out.
		 *
		 * FIXME for now we only allow REGISTER requests passing a single hardcoded
		 * Contact header so this doesn't apply to us yet.
		 */

		return sendRequest(RequestMethod.REGISTER, username, primaryHost,
				requestUri, callIdHeader, cseq, contactHeaders
				.toArray(new ContactHeader[contactHeaders.size()]));
	}

	//TODO later implement the OPTIONS method.
	//	public void sendOptionsRequest(String remoteUser, String remoteHost) {
	//		sendRequest(RequestMethod.OPTIONS, remoteUser, remoteHost,
	//				...);
	//	}
	//TODO when we do it, make sure that no dialog and session state is
	//messed up with by the flow of incoming responses to this OPTIONS request.

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

		//TODO *IF* request is a INVITE, make sure to add the following headers:
		/*
		 * (according to section 13.2.1)
		 *
		 * Allow
		 * Supported
		 * (later support also: Accept, Expires, adding body and body-related headers)
		 * (later support also: the offer/answer model
		 */

		SipURI contactUri;
		try {
			contactUri = addressMaker.createSipURI(username, localIp);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the contact URI for {} at {}." +
					"[username] must be a valid id, [localIp] must be a valid " +
					"IP address: {}", username, localIp, parseException.getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}
		contactUri.setPort(localPort);
		Address contactAddress = addressMaker.createAddress(contactUri);
		ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
		try {
			contactHeader.setExpires(60);
		} catch (InvalidArgumentException ignore) {}
		//TODO later, allow for multiple contact headers here too (the ones REGISTERed).

		Header[] additionalHeaders = ((List<ContactHeader>)(Collections
				.singletonList(contactHeader))).toArray(new ContactHeader[1]);

		return sendRequest(RequestMethod.INVITE, remoteUser, remoteHost,
				requestUri, callIdHeader, cseq, additionalHeaders);
	}

	private boolean sendRequest(RequestMethod method, String remoteUser,
			String remoteHost, URI requestUri, CallIdHeader callIdHeader, long cseq,
			Header... additionalHeaders) {
		try {
			URI addresserUri = addressMaker.createSipURI(username, primaryHost);
			URI addresseeUri = addressMaker.createSipURI(remoteUser, remoteHost);
			return sendRequest(method, requestUri, addresserUri, addresseeUri, null,
					callIdHeader, cseq, additionalHeaders);
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
		return sendRequest(RequestMethod.BYE, dialog);
	}

	private boolean sendRequest(RequestMethod method, Dialog dialog,
			Header... additionalHeaders) {
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
				callIdHeader, cseq, additionalHeaders);
	}

	private boolean sendRequest(final RequestMethod method, URI requestUri,
			URI addresserUri, URI addresseeUri, final Dialog dialog,
			final CallIdHeader callIdHeader, long cseq, Header... additionalHeaders) {
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
			viaHeader.setRPort();
			final Request request = messenger.createRequest(requestUri, method.toString(),
					callIdHeader, headerMaker.createCSeqHeader(cseq, method.toString()),
					headerMaker.createFromHeader(from, fromTag),
					headerMaker.createToHeader(to, toTag),
					Collections.singletonList(viaHeader),
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
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						if (doSendRequest(request, clientTransaction, dialog)) {
							logger.info("{} request sent.", method);
						}
						else {
							logger.error("Could not send this {} request.", method);
							reportRequestError(callIdHeader.getCallId(), clientTransaction,
									"Request could not be parsed or contained invalid state.");
						}
					} catch (SipException requestCouldNotBeSent) {
						logger.error("Could not send this {} request: {} ({}).",
								method, requestCouldNotBeSent.getMessage(),
								requestCouldNotBeSent.getCause().getMessage());
						reportRequestError(callIdHeader.getCallId(), clientTransaction,
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
				bus.post(new RegistrationFailed(errorMessage));
				break;
			case INVITE:
				bus.post(new CallInvitationFailed(errorMessage, callId));
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
						handleRegisterResponse(statusCode, response);
						break;
					case INVITE:
						handleInviteResponse(statusCode, clientTransaction);
						break;
					case BYE:
						handleByeResponse(statusCode, clientTransaction);
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
		if (response != null) {
			ListIterator<?> iterator = response.getHeaders(ViaHeader.NAME);
			int viaHeaderCount = 0;
			while (iterator.hasNext()) {
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
			logger.error("A Fatal error occurred.{}", statusCode,
					callIdInAdvance != null ? "" : " If it was NOT during processing of" +
							" a REGISTER request, no end-user callback might get fired.");
			//Just in case this error is associated with a REGISTER or INVITE request,
			//a RegistrationFailed event and a CallInvitationFailed event are sent.");
			bus.post(new RegistrationFailed(codeAndReason));
			if (callIdInAdvance != null) {
				bus.post(new CallInvitationFailed(codeAndReason, callIdInAdvance));
			}
			return;
		}
		Request request = clientTransaction.getRequest();
		String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		switch (Constants.getRequestMethod(request.getMethod())) {
			case REGISTER:
				bus.post(new RegistrationFailed(codeAndReason));
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
		while (usedAuthHeaders.hasNext()) {
			AuthorizationHeader authHeader = (AuthorizationHeader)
					usedAuthHeaders.next();
			possiblyFailedAuthRealms.put(authHeader.getRealm(),
					authHeader.getNonce());
		}

		Map<String, String> possiblyFailedProxyAuthRealms = new HashMap<>();
		ListIterator<?> usedProxyAuthHeaders = request
				.getHeaders(ProxyAuthorizationHeader.NAME);
		while (usedProxyAuthHeaders.hasNext()) {
			ProxyAuthorizationHeader authHeader = (ProxyAuthorizationHeader)
					usedProxyAuthHeaders.next();
			possiblyFailedProxyAuthRealms.put(authHeader.getRealm(),
					authHeader.getNonce());
		}

		boolean worthAuthenticating = false;
		ListIterator<?> wwwAuthenticateHeaders = response
				.getHeaders(WWWAuthenticateHeader.NAME);
		while (wwwAuthenticateHeaders.hasNext()) {
			WWWAuthenticateHeader wwwAuthenticateHeader =
					(WWWAuthenticateHeader) wwwAuthenticateHeaders.next();
			String realm = wwwAuthenticateHeader.getRealm();
			String nonce = wwwAuthenticateHeader.getNonce();
			if (authNoncesCache.containsKey(toHeaderValue)
					&& authNoncesCache.get(toHeaderValue).containsKey(realm)) {
				String oldNonce = authNoncesCache.get(toHeaderValue).get(realm);
				if (possiblyFailedAuthRealms.containsKey(realm)) {
					if (oldNonce.equals(possiblyFailedAuthRealms.get(realm))) {
						authNoncesCache.get(toHeaderValue).remove(realm);
						continue;
					}
				}
			}
			worthAuthenticating = addAuthorizationHeader(request, hostUri,
					toHeaderValue, username, password, realm, nonce);
			if (!authNoncesCache.containsKey(toHeaderValue)) {
				authNoncesCache.put(toHeaderValue, new HashMap<String, String>());
			}
			authNoncesCache.get(toHeaderValue).put(realm, nonce);
		}
		ListIterator<?> proxyAuthenticateHeaders = response
				.getHeaders(ProxyAuthenticateHeader.NAME);
		while (proxyAuthenticateHeaders.hasNext()) {
			ProxyAuthenticateHeader proxyAuthenticateHeader =
					(ProxyAuthenticateHeader) proxyAuthenticateHeaders.next();
			String realm = proxyAuthenticateHeader.getRealm();
			String nonce = proxyAuthenticateHeader.getNonce();
			if (proxyAuthNoncesCache.containsKey(toHeaderValue)
					&& proxyAuthNoncesCache.get(toHeaderValue).containsKey(realm)) {
				String oldNonce = proxyAuthNoncesCache.get(toHeaderValue).get(realm);
				if (possiblyFailedProxyAuthRealms.containsKey(realm)) {
					if (oldNonce.equals(possiblyFailedProxyAuthRealms.get(realm))) {
						proxyAuthNoncesCache.get(toHeaderValue).remove(realm);
						proxyAuthCallIdCache.get(toHeaderValue).remove(realm);
						continue;
					}
				}
			}
			worthAuthenticating = addProxyAuthorizationHeader(request, hostUri,
					toHeaderValue, username, password, realm, nonce);
			if (!proxyAuthNoncesCache.containsKey(toHeaderValue)) {
				proxyAuthNoncesCache.put(toHeaderValue, new HashMap<String, String>());
				proxyAuthCallIdCache.put(toHeaderValue, new HashMap<String, String>());
			}
			proxyAuthNoncesCache.get(toHeaderValue).put(realm, nonce);
			Dialog dialog = clientTransaction.getDialog();
			String callId = dialog.getCallId().getCallId();
			if (dialog != null) {
				proxyAuthCallIdCache.get(toHeaderValue).put(realm, callId);
			}
		}

		if (worthAuthenticating) {
			try {
				if (doSendRequest(request, null, clientTransaction.getDialog(), false)) {
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
						"credentials: {} ({}).", request.getMethod(),
						requestCouldNotBeSent.getMessage(),
						requestCouldNotBeSent.getCause().getMessage());
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

		ListIterator<?> acceptEncodingHeaders =
				response.getHeaders(AcceptEncodingHeader.NAME);
		List<String> acceptedEncodings = new LinkedList<>();
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
		ContentEncodingHeader contentEncodingHeader =
				request.getContentEncoding();
		String definedEncoding = contentEncodingHeader.getEncoding();
		StringBuilder overlappingEncodings = new StringBuilder();
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
		boolean overlappingEncodingsFound = false;
		if (overlappingEncodings.length() > 0) {
			try {
				contentEncodingHeader
					.setEncoding(overlappingEncodings.toString());
				request.setContentEncoding(contentEncodingHeader);
				overlappingEncodingsFound = true;
			} catch (ParseException ignore) {}
		}
		
		boolean shouldBypassContentTypesCheck = false;
		ListIterator<?> acceptHeaders = response.getHeaders(AcceptHeader.NAME);
		Map<String, String> typeSubtypeToQValue = new HashMap<>();
		Map<String, Set<String>> typeToSubTypes = new HashMap<>();
		while (acceptHeaders.hasNext()) {
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
			ListIterator<?> definedContentTypeHeaders =
					request.getHeaders(ContentTypeHeader.NAME);
			List<ContentTypeHeader> overlappingContentTypes
					= new LinkedList<>();
			while (definedContentTypeHeaders.hasNext()) {
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
			sendByeRequest(dialog);
		}
	}

	private void handleRegisterResponse(int statusCode, Response response) {
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			logger.info("{} response to REGISTER arrived.", statusCode);
			bus.post(new RegistrationSuccess(response.getHeaders(ContactHeader.NAME)));
		}
	}

	private void handleInviteResponse(int statusCode, ClientTransaction clientTransaction) {
		Request request = clientTransaction.getRequest();
		String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		Dialog dialog = clientTransaction.getDialog();
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			if (dialog != null) {
				try {
					CSeqHeader cseqHeader = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
					Request ackRequest = dialog.createAck(cseqHeader.getSeqNumber());
					//TODO *IF* the INVITE request contained a offer, this ACK
					//MUST carry an answer to that offer, given that the offer is acceptable!
					//TODO *HOWEVER* if the offer is not acceptable, after sending the ACK,
					//a BYE request MUST be sent immediately.
					dialog.sendAck(ackRequest);
					logger.info("{} response to INVITE arrived, so {} sent.", statusCode,
							RequestMethod.ACK);
					logger.info("New call established: {}.", callId);
					bus.post(new CallInvitationAccepted(callId, dialog));
				} catch (InvalidArgumentException ignore) {
				} catch (SipException ignore) {}
			}
		}
		else if (ResponseClass.PROVISIONAL == Constants.getResponseClass(statusCode)) {
			if (statusCode == Response.RINGING) {
				logger.info("Ringing!");
				bus.post(new CallInvitationRinging(callId, clientTransaction));
			}
			else {
				bus.post(new CallInvitationWaiting(callId, clientTransaction));
			}
			logger.info("{} response arrived.", statusCode);
		}
	}

	private void handleByeResponse(int statusCode, ClientTransaction clientTransaction) {
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			logger.info("{} response to BYE arrived.", statusCode);
			handleThisRequestTerminated(clientTransaction);
			String callId = null; Dialog dialog = clientTransaction.getDialog();
			if (dialog != null) {
				callId = dialog.getCallId().getCallId();
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
				bus.post(new CallInvitationCanceled("Callee canceled " +
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
		while (iterator.hasNext()) {
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
			cseq.setSeqNumber(newCSeq);
			if (localCSeq < newCSeq) {
				localCSeq = newCSeq;
			}
		} catch (InvalidArgumentException ignore) {}
		request.setHeader(cseq);
	}

	private boolean doSendRequest(Request request,
			ClientTransaction clientTransaction, Dialog dialog)
			throws TransactionUnavailableException, SipException {
		return doSendRequest(request, clientTransaction, dialog, true);
	}

	private boolean doSendRequest(Request request, ClientTransaction clientTransaction,
			Dialog dialog, boolean tryAddingAuthorizationHeaders)
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
							toHeaderValue, toHeaderValue, realm, probableRealms.get(realm));
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
								toHeaderValue, toHeaderValue, realm,
								probableRealms.get(realm));
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
				viaHeader.setRPort();
			} catch (ParseException ignore) {
			} catch (InvalidArgumentException ignore) {}
		}
		if (dialog != null) {
			try {
				logger.info("Sending {} request...", request.getMethod());
				dialog.sendRequest(newClientTransaction);
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
			logger.info("Sending {} request...", request.getMethod());
			newClientTransaction.sendRequest();
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
		request.addHeader(proxyAuthorizationHeader);
		//ProxyAuthorization header could be added.
		return true;
	}
	
}
