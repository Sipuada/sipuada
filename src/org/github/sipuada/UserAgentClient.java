package org.github.sipuada;

import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

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
import android.javax.sip.TransactionAlreadyExistsException;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.header.AuthorizationHeader;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.ProxyAuthenticateHeader;
import android.javax.sip.header.ProxyAuthorizationHeader;
import android.javax.sip.header.ToHeader;
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
				handleAuthorizationRequired(statusCode, response, clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.REQUEST_ENTITY_TOO_LARGE:
				//TODO handle this by retrying omitting the body or using one of
				//smaller length.
				//If the condition is temporary, the server SHOULD include a
				//Retry-After header field to indicate that it is temporary and after
				//what time the client MAY try again.
				handleRequestEntityTooLarge(clientTransaction);
				//No method-specific handling is required.
				return false;
			case Response.UNSUPPORTED_MEDIA_TYPE:
				//TODO handle this by retrying after filtering any media types not listed in
				//the Accept header field in the response, with encodings listed in the
				//Accept-Encoding header field in the response, and with languages listed in
				//the Accept-Language in the response.
				//No method-specific handling is required.
				return false;
			case Response.UNSUPPORTED_URI_SCHEME:
				//TODO handle this by retrying, this time using a SIP(S) URI.
				//No method-specific handling is required.
				return false;
			case Response.BAD_EXTENSION:
				//TODO handle this by retrying, this time omitting any extensions listed in
				//the Unsupported header field in the response.
				//No method-specific handling is required.
				return false;
				
			//FIXME In all of the above cases, the request is retried by creating
			//a new request with the appropriate modifications.
			//This new request constitutes a new transaction and SHOULD have the same value
			//of the Call-ID, To, and From of the previous request, but the CSeq
			//should contain a new sequence number that is one higher than the previous.

			case Response.REQUEST_TIMEOUT:
			case Response.SERVER_TIMEOUT:
				//FIXME I think no method-specific handling is required.
				//TODO handle this by retrying the same request after a while.
				return false;
				
			case Response.ADDRESS_INCOMPLETE:
				//FIXME I think no method-specific handling is required.
				//TODO figure out how to handle this by doing overlapped dialing(?)
				//until the response no longer is a 484 (Address Incomplete).
				return false;
				
			case Response.AMBIGUOUS:
				//FIXME I think no method-specific handling is required.
				//TODO figure out how to handle this by prompting for user intervention
				//for deciding which of the choices provided is to be used in the retry.
				return false;

			case Response.BUSY_HERE:
			case Response.BUSY_EVERYWHERE:
			case Response.DECLINE:
				//No method-specific handling is required.
				//TODO handle this by retrying if a better time to call is indicated in
				//the Retry-After header field.
				return false;
				
			case Response.SERVER_INTERNAL_ERROR:
			case Response.SERVICE_UNAVAILABLE:
				//No method-specific handling is required.
				//TODO handle this by retrying if a better time to call is indicated in
				//the Retry-After header field.
				return false;
				
			case Response.TRYING:
				//FIXME what about these? Is a method-specific handling required?
				//TODO handle these situations.
				return true;
		}
		switch (Constants.getResponseClass(statusCode)) {
			case PROVISIONAL:
				//TODO give the application layer feedback on this event.
				return true;
			case SUCCESS:
				return true;
			case REDIRECT:
				//TODO perform redirect request(s) transparently.
				return false;
			case CLIENT_ERROR:
			case SERVER_ERROR:
			case GLOBAL_ERROR:
				//TODO give the application layer feedback on this error.
				return false;
			case UNKNOWN:
				//Handle this by simply discarding this unknown response.
				return false;
		}
		return true;
	}
	
	private void handleAuthorizationRequired(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		Request originalRequest = clientTransaction.getRequest();
		Request clonedRequest = cloneRequest(originalRequest);
		CSeqHeader cseq = (CSeqHeader) originalRequest.getHeader(CSeqHeader.NAME);
		try {
			cseq.setSeqNumber(cseq.getSeqNumber() + 1);
		} catch (InvalidArgumentException ignore) {}
		clonedRequest.setHeader(cseq);

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
			
			ToHeader toHeader = (ToHeader) clonedRequest.getHeader(ToHeader.NAME);
			String toHeaderValue = toHeader.getAddress().getURI().toString();

			@SuppressWarnings("rawtypes")
			ListIterator wwwAuthenticateHeaders = response
					.getHeaders(WWWAuthenticateHeader.NAME);
			while (wwwAuthenticateHeaders.hasNext()) {
				WWWAuthenticateHeader wwwAuthenticateHeader 
					= (WWWAuthenticateHeader) wwwAuthenticateHeaders.next();
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
						password, clonedRequest.getMethod(), domainUri.toString(), nonce);
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
				clonedRequest.addHeader(authorizationHeader);
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
				ProxyAuthenticateHeader proxyAuthenticateHeader
					= (ProxyAuthenticateHeader) proxyAuthenticateHeaders.next();
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
						password, clonedRequest.getMethod(), domainUri.toString(), nonce);
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
				clonedRequest.addHeader(proxyAuthorizationHeader);
				if (!noncesCache.containsKey(toHeaderValue)) {
					noncesCache.put(toHeaderValue, new HashMap<String, String>());
				}
				noncesCache.get(toHeaderValue).put(realm, nonce);
				worthAuthenticating = true;
			}
			
			if (worthAuthenticating) {
				try {
					ClientTransaction transaction = provider.getNewClientTransaction(clonedRequest);
					transaction.sendRequest();
				} catch (TransactionAlreadyExistsException ignore) {
				} catch (TransactionUnavailableException newTransactionCouldNotBeCreated) {
					
				} catch (SipException requestCouldNotBeSent) {
					
				}
			}
		} catch (ParseException parseException) {
			
		}
	}
	
	private void handleRequestEntityTooLarge(ClientTransaction clientTransaction) {
		
	}

	private void handleInviteResponse(int statusCode, ClientTransaction clientTransaction) {
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
			}
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

}
