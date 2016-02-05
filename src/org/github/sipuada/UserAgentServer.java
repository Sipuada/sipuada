package org.github.sipuada;

import java.text.ParseException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionState;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class UserAgentServer {

	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;
	private Sipuada.Listener requestsListener;
	private Map<String, ServerTransaction> inviteServerTransactions;
	private Logger logger = LoggerFactory.getLogger(UserAgentServer.class);

	public UserAgentServer(SipProvider sipProvider, MessageFactory messageFactory, HeaderFactory headerFactory) {
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
	}

	public void processRequest(RequestEvent requestEvent) {
		ServerTransaction serverTransaction = requestEvent.getServerTransaction();
		if (serverTransaction != null) {
			Request request = requestEvent.getRequest();
			String requestMethod = request.getMethod();
			handlerResquest(requestMethod, serverTransaction);
		}
	}

	private void handlerResquest(String requestMethod, ServerTransaction serverTransaction) {
		switch (Constants.getRequestMethod(requestMethod)) {
		/*
		 * - The UAS application can accept the invitation by sending a 2xx
		 * response to the UAC, a 2xx response to an INVITE transaction
		 * establishes a session.
		 * 
		 * - If the UAS is not able to answer the invitation immediately, it can
		 * choose to indicate some kind of progress to the UAC (for example, an
		 * indication that a phone is ringing). This is accomplished with a
		 * provisional response between 101 and 199.
		 * 
		 * - If the invitation is not accepted, a 3xx, 4xx, 5xx or 6xx response
		 * is sent by the application, depending on the reason for the
		 * rejection.
		 */
		case INVITE:
			handleInviteRequest(serverTransaction);
			break;
		/*
		 * - A UAS core receiving a BYE request checks if it matches an existing
		 * dialog. If the BYE does not match an existing dialog, the UAS core
		 * SHOULD generate a 481 (Call/Transaction Does Not Exist) response and
		 * pass that to the server transaction.
		 * 
		 * - UAS SHOULD terminate the session (and therefore stop sending and
		 * listening for media) - Whether or not it ends its participation on
		 * the session, (considering multicast sessions), the UAS core MUST
		 * generate a 2xx response to the BYE.
		 */
		case BYE:
			handleByeRequest(serverTransaction);
			break;

		/*
		 * - If the UAS did not find a matching transaction for the CANCEL
		 * according to the procedure above, it SHOULD respond to the CANCEL
		 * with a 481 (Call Leg/Transaction Does Not Exist).
		 * 
		 * - If the UAS has not issued a final response for the original
		 * request, its behavior depends on the method of the original request.
		 * If the original request was an INVITE, the UAS SHOULD immediately
		 * respond to the INVITE with a 487 (Request Terminated).
		 * 
		 * - Regardless of the method of the original request, as long as the
		 * CANCEL matched an existing transaction, the UAS answers the CANCEL
		 * request itself with a 200 (OK) response(See Section 8.2.6).
		 */
		case CANCEL:
			handleCancelRequest(serverTransaction);
			break;
		default:
			break;
		}
	}

	private void handleCancelRequest(ServerTransaction serverTransaction) {
		try {
			Response response = messenger.createResponse(Response.OK, serverTransaction.getRequest());
			serverTransaction.sendResponse(response);
		} catch (ParseException | SipException | InvalidArgumentException e) {
			e.printStackTrace();
		}
		String cancelCallId = serverTransaction.getDialog().getCallId().getCallId();
		ServerTransaction canceledTransaction = getServerTransationByCallId(cancelCallId);
		if (canceledTransaction != null && !isTransactionCompleted(canceledTransaction)) {
			logger.debug(" Cancel Request handled: sent request terminated.");
			try {
				Response response = messenger.createResponse(Response.REQUEST_TERMINATED,
						canceledTransaction.getRequest());
				canceledTransaction.sendResponse(response);
			} catch (SipException | InvalidArgumentException | ParseException e) {
				e.printStackTrace();
			}
		}else{
			logger.debug(" Cancel Request handled: request already finished.");
		}
	
	
	}

	private boolean isTransactionCompleted(ServerTransaction transaction) {
		return transaction.getState().equals(TransactionState.COMPLETED)
				|| transaction.getState().equals(TransactionState.TERMINATED);
	}

	private void handleByeRequest(ServerTransaction serverTransaction) {
		Dialog dialog = serverTransaction.getDialog();
		if (dialog == null) {
			logger.debug(" Bye Request handled: Dialog is null");
			Response response;
			try {
				response = messenger.createResponse(Response.REQUEST_TERMINATED, serverTransaction.getRequest());
				serverTransaction.sendResponse(response);
			} catch (ParseException | InvalidArgumentException | SipException e) {
				e.printStackTrace();
			}
		} else {
			logger.debug(" Bye Request handled: Sent ok response");
			try {
				Response response = messenger.createResponse(Response.OK, serverTransaction.getRequest());
				serverTransaction.sendResponse(response);
			} catch (ParseException | SipException | InvalidArgumentException e) {
				e.printStackTrace();
			}
		}

	}

	private void handleInviteRequest(ServerTransaction serverTransaction) {
		
		if (requestsListener != null) {
			String callId = serverTransaction.getDialog().getCallId().getCallId();
			if (requestsListener.onCallReceived(callId)) {
				logger.debug(" Invite Request handled: sent Ringing Response");
				sendRingingResponse(serverTransaction);
				inviteServerTransactions.put(callId, serverTransaction);
			} else {
				try {
					Response response = messenger.createResponse(Response.BUSY_HERE, serverTransaction.getRequest());
					serverTransaction.sendResponse(response);
					logger.debug(" Invite Request handled: sent busy here");
				} catch (ParseException | InvalidArgumentException | SipException e) {
					e.printStackTrace();
				}
			}
		} else {
			sendRingingResponse(serverTransaction);
		}

	}

	private void sendRingingResponse(ServerTransaction serverTransaction) {
		try {
			Response response = messenger.createResponse(Response.RINGING, serverTransaction.getRequest());
			serverTransaction.sendResponse(response);
		} catch (ParseException | InvalidArgumentException | SipException e) {
			e.printStackTrace();
		}
	}

	public void setRequestsListener(Sipuada.Listener listener) {
		this.requestsListener = listener;
	}

	public void processRetransmission(TimeoutEvent retransmissionEvent) {
		if (retransmissionEvent.isServerTransaction()) {
			ServerTransaction serverTransaction = retransmissionEvent.getServerTransaction();
			// TODO Dialog layer says we should retransmit a response. how?
		}
	}

	public ServerTransaction getServerTransationByCallId(String callId) {
		return inviteServerTransactions.get(callId);
	}

	public void sendResponse(int method, String callId) {
		ServerTransaction serverTransaction = getServerTransationByCallId(callId);
		try {
			Response response = messenger.createResponse(method, serverTransaction.getRequest());
			serverTransaction.sendResponse(response);
		} catch (SipException | InvalidArgumentException | ParseException e) {
			e.printStackTrace();
		}
	}

}
