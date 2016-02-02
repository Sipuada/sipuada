package org.github.sipuada;

import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.github.sipuada.Constants.ResponseClass;

import android.gov.nist.gnjvx.sip.address.SipUri;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionDoesNotExistException;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.header.AcceptEncodingHeader;
import android.javax.sip.header.AcceptHeader;
import android.javax.sip.header.AuthorizationHeader;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContentEncodingHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.ProxyAuthenticateHeader;
import android.javax.sip.header.ProxyAuthorizationHeader;
import android.javax.sip.header.ProxyRequireHeader;
import android.javax.sip.header.RequireHeader;
import android.javax.sip.header.RetryAfterHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.UnsupportedHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.header.WWWAuthenticateHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class UserAgentClient {

	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;
	private final String username;
	private final String domain;
	private final String password;
	private final Map<String, Map<String, String>> noncesCache;
	
	public UserAgentClient(SipProvider sipProvider, MessageFactory messageFactory,
			HeaderFactory headerFactory, Map<String, Map<String, String>> cache,
			String... credentials) {
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
		noncesCache = cache;
		username = credentials.length > 0 && credentials[0] != null ? credentials[0] : "";
		domain = credentials.length > 1 && credentials[1] != null ? credentials[1] : "";
		password = credentials.length > 2 && credentials[2] != null ? credentials[2] : "";
	}

	public void sendRequest() {
		
	}
	
	public void processResponse(ResponseEvent responseEvent) {
		ClientTransaction clientTransaction = responseEvent.getClientTransaction();
		if (clientTransaction != null) {
			Response response = responseEvent.getResponse();
			int statusCode = response.getStatusCode();
			handleResponse(statusCode, response, clientTransaction);
		}
		else {
			//No request made by this UAC is associated with this response.
			//The response then is considered stray, so it simply discards it.
		}
	}

	public void processTimeout(TimeoutEvent timeoutEvent) {
		if (!timeoutEvent.isServerTransaction()) {
			ClientTransaction clientTransaction = timeoutEvent.getClientTransaction();
			handleResponse(Response.REQUEST_TIMEOUT, null, clientTransaction);
		}
	}

	public void processFatalTransportError(IOExceptionEvent exceptionEvent) {
		handleResponse(Response.SERVICE_UNAVAILABLE, null, null);
	}
	
	private void handleResponse(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		if (tryHandlingResponseGenerically(statusCode, response, clientTransaction)) {
			switch (Constants.getRequestMethod(clientTransaction.getRequest().getMethod())) {
				case INVITE:
					handleInviteResponse(statusCode, clientTransaction);
					break;
				case UNKNOWN:
				default:
					break;
			}
		}
	}
	
	private boolean tryHandlingResponseGenerically(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		@SuppressWarnings("rawtypes")
		ListIterator iterator = response.getHeaders(ViaHeader.NAME);
		int viaHeaderCount = 0;
		while (iterator.hasNext()) {
			iterator.next();
			viaHeaderCount++;
		}
		if (viaHeaderCount >= 1) {
			//Handle by simply discarding the response, it's corrupted.
			return false;
		}
		switch (statusCode) {
			case Response.PROXY_AUTHENTICATION_REQUIRED:
			case Response.UNAUTHORIZED:
				//Handle this by performing authorization procedures.
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
				//TODO handle this by retrying after filtering any media types not listed in
				//the Accept header field in the response, with encodings listed in the
				//Accept-Encoding header field in the response, and with languages listed in
				//the Accept-Language in the response.
				handleUnsupportedMediaTypes(response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.BAD_EXTENSION:
				//TODO handle this by retrying, this time omitting any extensions listed in
				//the Unsupported header field in the response.
				handleUnsupportedExtension(response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.NOT_FOUND:
			case Response.BUSY_HERE:
			case Response.BUSY_EVERYWHERE:
			case Response.DECLINE:
				//TODO handle this by retrying if a better time to call is indicated in
				//the Retry-After header field.
				handleByReschedulingIfApplicable(response, clientTransaction, false);
				//No method-specific handling is required.
				return false;
			case Response.SERVER_INTERNAL_ERROR:
			case Response.SERVICE_UNAVAILABLE:
				//TODO handle this by retrying if expected available time is indicated in
				//the Retry-After header field.
				handleByReschedulingIfApplicable(response, clientTransaction, false);
				//No method-specific handling is required.
				return false;
			case Response.REQUEST_TIMEOUT:
			case Response.SERVER_TIMEOUT:
				//TODO handle this by retrying the same request after a while if it is
				//outside of a dialog, otherwise consider the dialog and session terminated.
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
		}
		switch (Constants.getResponseClass(statusCode)) {
			case PROVISIONAL:
				//TODO give the application layer feedback on this event.
				return true;
			case SUCCESS:
				return true;
			case REDIRECT:
				//TODO perform redirect request(s) transparently.
				//When implementing this, remember to implement the AMBIGUOUS case above.
				return false;
			case CLIENT_ERROR:
			case SERVER_ERROR:
			case GLOBAL_ERROR:
				//TODO report this response error back to the application layer.
				return false;
			case UNKNOWN:
				//Handle this by simply discarding this unknown response.
				return false;
		}
		return true;
	}
	
	private void handleAuthorizationRequired(Response response,
			ClientTransaction clientTransaction) {
		Request request = cloneRequest(clientTransaction.getRequest());
		incrementCSeq(request);

		try {
			boolean worthAuthenticating = false;
			
			SipUri domainUri = null;
			if (domain.trim().isEmpty()) {
				domainUri = new SipUri();
				domainUri.setHost(domain);
			}
			String username = this.username, password = this.password;
			if (username.trim().isEmpty()) {
				username = "anonymous";
				password = "";
			}
			
			ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
			String toHeaderValue = toHeader.getAddress().getURI().toString();

			@SuppressWarnings("rawtypes")
			ListIterator wwwAuthenticateHeaders = response
					.getHeaders(WWWAuthenticateHeader.NAME);
			while (wwwAuthenticateHeaders.hasNext()) {
				WWWAuthenticateHeader wwwAuthenticateHeader =
						(WWWAuthenticateHeader) wwwAuthenticateHeaders.next();
				String realm = wwwAuthenticateHeader.getRealm();
				String nonce = wwwAuthenticateHeader.getNonce();
				if (noncesCache.containsKey(toHeaderValue)
						&& noncesCache.get(toHeaderValue).containsKey(realm)) {
					String oldNonce = noncesCache.get(toHeaderValue).get(realm);
					if (oldNonce.equals(nonce)) {
						continue;
					}
				}
				String responseDigest = AuthorizationDigest.getDigest(username, realm,
						password, request.getMethod(), domainUri.toString(), nonce);
				AuthorizationHeader authorizationHeader = headerMaker
						.createAuthorizationHeader("Digest");
				authorizationHeader.setAlgorithm("MD5");
				if (domainUri != null) {
					authorizationHeader.setURI(domainUri);
				}
				authorizationHeader.setUsername(username);
				authorizationHeader.setRealm(realm);
				authorizationHeader.setNonce(nonce);
				authorizationHeader.setResponse(responseDigest);
				request.addHeader(authorizationHeader);
				if (!noncesCache.containsKey(toHeaderValue)) {
					noncesCache.put(toHeaderValue, new HashMap<String, String>());
				}
				noncesCache.get(toHeaderValue).put(realm, nonce);
				worthAuthenticating = true;
			}
			@SuppressWarnings("rawtypes")
			ListIterator proxyAuthenticateHeaders = response
					.getHeaders(ProxyAuthenticateHeader.NAME);
			while (proxyAuthenticateHeaders.hasNext()) {
				ProxyAuthenticateHeader proxyAuthenticateHeader =
						(ProxyAuthenticateHeader) proxyAuthenticateHeaders.next();
				String realm = proxyAuthenticateHeader.getRealm();
				String nonce = proxyAuthenticateHeader.getNonce();
				if (noncesCache.containsKey(toHeaderValue)
						&& noncesCache.get(toHeaderValue).containsKey(realm)) {
					String oldNonce = noncesCache.get(toHeaderValue).get(realm);
					if (oldNonce.equals(nonce)) {
						continue;
					}
				}
				String responseDigest = AuthorizationDigest.getDigest(username, realm,
						password, request.getMethod(), domainUri.toString(), nonce);
				ProxyAuthorizationHeader proxyAuthorizationHeader = headerMaker
						.createProxyAuthorizationHeader("Digest");
				proxyAuthorizationHeader.setAlgorithm("MD5");
				if (domainUri != null) {
					proxyAuthorizationHeader.setURI(domainUri);
				}
				proxyAuthorizationHeader.setUsername(username);
				proxyAuthorizationHeader.setRealm(realm);
				proxyAuthorizationHeader.setNonce(nonce);
				proxyAuthorizationHeader.setResponse(responseDigest);
				request.addHeader(proxyAuthorizationHeader);
				if (!noncesCache.containsKey(toHeaderValue)) {
					noncesCache.put(toHeaderValue, new HashMap<String, String>());
				}
				noncesCache.get(toHeaderValue).put(realm, nonce);
				worthAuthenticating = true;
			}
			
			if (worthAuthenticating) {
				try {
					doSendRequest(request, clientTransaction.getDialog());
				} catch (SipException requestCouldNotBeSent) {
					//Request that would authenticate could not be sent.
					//TODO report 401/407 error back to application layer.
				}
			}
			else {
				//Not worth authenticating because server already denied
				//this exact request.
				//TODO report 401/407 error back to application layer.
			}
		} catch (ParseException parseException) {
			//TODO report 401/407 error back to application layer.
		}
	}
	
	private void handleUnsupportedMediaTypes(Response response,
			ClientTransaction clientTransaction) {
		Request request = cloneRequest(clientTransaction.getRequest());
		incrementCSeq(request);

		@SuppressWarnings("rawtypes")
		ListIterator acceptEncodingHeaders =
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
		@SuppressWarnings("rawtypes")
		ListIterator acceptHeaders = response.getHeaders(AcceptHeader.NAME);
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
			@SuppressWarnings("rawtypes")
			ListIterator definedContentTypeHeaders =
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
				doSendRequest(request, clientTransaction.getDialog());
			} catch (SipException requestCouldNotBeSent) {
				//Request that would amend this situation could not be sent.
				//TODO report 415 error back to application layer.
			}
		}
		else {
			//Cannot satisfy the media type requirements since this UAC doesn't
			//support any that are accepted by the UAS that sent this response.
			//TODO report 415 error back to application layer.
		}
	}
	
	private void handleUnsupportedExtension(Response response,
			ClientTransaction clientTransaction) {
		Request request = cloneRequest(clientTransaction.getRequest());
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
			doSendRequest(request, clientTransaction.getDialog());
		} catch (SipException requestCouldNotBeSent) {
			//Request that would amend this situation could not be sent.
			//TODO report 420 error back to application layer.
		}
	}

	private void handleByReschedulingIfApplicable(Response response,
			final ClientTransaction clientTransaction, boolean abortIfInDialog) {
		if (abortIfInDialog) {
			Dialog dialog = clientTransaction.getDialog();
			if (dialog != null) {
				dialog.delete();
				//TODO report response error back to application layer.
				return;
			}
		}

		final Request request = cloneRequest(clientTransaction.getRequest());
		incrementCSeq(request);

		RetryAfterHeader retryAfterHeader =
				(RetryAfterHeader) response.getHeader(RetryAfterHeader.NAME);
		if (retryAfterHeader != null) {
			int retryAfterSeconds = retryAfterHeader.getRetryAfter();
			int durationSeconds = retryAfterHeader.getDuration();
			if (durationSeconds == 0 || durationSeconds > 300) {
				Timer timer = new Timer();
				timer.schedule(new TimerTask() {
					
					@Override
					public void run() {
						try {
							doSendRequest(request, clientTransaction.getDialog());
						} catch (SipException requestCouldNotBeSent) {
							//Could not reschedule request.
							//TODO report response error back to application layer.
						}
					}

				}, retryAfterSeconds * 1000);
				return;
			}
		}
		//Request is not allowed to reschedule or it would be pointless to do so
		//because availability frame is too short.
		//TODO report response error back to application layer.
	}
	
	private void handleInviteResponse(int statusCode, ClientTransaction clientTransaction) {
		boolean shouldSaveDialog = false;
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			Dialog dialog = clientTransaction.getDialog();
			if (dialog != null) {
				try {
					CSeqHeader cseqHeader = (CSeqHeader) clientTransaction
							.getRequest().getHeader(CSeqHeader.NAME);
					Request ackRequest = dialog.createAck(cseqHeader.getSeqNumber());
					dialog.sendAck(ackRequest);
				} catch (InvalidArgumentException ignore) {
				} catch (SipException ignore) {}
				shouldSaveDialog = true;
			}
		}
		else if (ResponseClass.PROVISIONAL == Constants.getResponseClass(statusCode)) {
			if (statusCode == Response.RINGING) {
				//TODO report response status code back to the application layer.
			}
			shouldSaveDialog = true;
		}
		if (shouldSaveDialog) {
			//TODO figure out how to properly save this dialog so that future request intents will
			//know how to properly fetch it in order to properly build requests.
		}
	}
	
	private Request cloneRequest(Request original) {
		Request clone = null;
		List<ViaHeader> viaHeaders = new LinkedList<>();
		@SuppressWarnings("rawtypes")
		ListIterator iterator = original.getHeaders(ViaHeader.NAME);
		while (iterator.hasNext()) {
			viaHeaders.add((ViaHeader) iterator.next());
		}
		try {
			clone = messenger.createRequest(original.getRequestURI(), original.getMethod(),
					(CallIdHeader) original.getHeader(CallIdHeader.NAME),
					(CSeqHeader) original.getHeader(CSeqHeader.NAME),
					(FromHeader) original.getHeader(FromHeader.NAME),
					(ToHeader) original.getHeader(ToHeader.NAME), viaHeaders,
					(MaxForwardsHeader) original.getHeader(MaxForwardsHeader.NAME),
					(ContentTypeHeader) original.getHeader(ContentTypeHeader.NAME),
					original.getRawContent());
		} catch (ParseException ignore) {}
		return clone;
	}

	private void incrementCSeq(Request request) {
		CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
		try {
			cseq.setSeqNumber(cseq.getSeqNumber() + 1);
		} catch (InvalidArgumentException ignore) {}
		request.setHeader(cseq);
	}

	private void doSendRequest(Request request, Dialog dialog)
			throws TransactionUnavailableException, SipException {
		ClientTransaction newClientTransaction =
				provider.getNewClientTransaction(request);
		if (dialog != null) {
			try {
				dialog.sendRequest(newClientTransaction);
			}
			catch (TransactionDoesNotExistException ignore) {}
		}
		else {
			newClientTransaction.sendRequest();
		}
	}

}
