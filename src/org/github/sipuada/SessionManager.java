package org.github.sipuada;

import java.text.ParseException;
import java.util.Map;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.github.sipuada.plugins.SipuadaPlugin.SessionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sdp.SdpFactoryImpl;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.Message;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SessionManager {

	private final static Logger logger = LoggerFactory.getLogger(SessionManager.class);

	private final HeaderFactory headerMaker;
	private final Map<RequestMethod, SipuadaPlugin> sessionPlugins;
	private final SipUserAgentRole role;
	private final String localAddress;

	public enum SipUserAgentRole {
		UAC, UAS;
	}

	public SessionManager(Map<RequestMethod, SipuadaPlugin> sessionPlugins,
		SipUserAgentRole role, String localAddress, HeaderFactory headerMaker) {
		this.headerMaker = headerMaker;
		this.sessionPlugins = sessionPlugins;
		this.role = role;
		this.localAddress = localAddress;
	}

	public boolean performOfferAnswerExchangeStep(String callId, SessionType type,
			Request request, Response response, Request ackRequest) {
		logger.debug("$ Performing OFFER/ANSWER exchange step {}/{}! $", callId, type);
		SipuadaPlugin sessionPlugin = null;
		if (request != null) {
			RequestMethod requestMethod = RequestMethod.UNKNOWN;
			try {
				requestMethod = RequestMethod.valueOf
					(request.getMethod().toUpperCase().trim());
			} catch (IllegalArgumentException ignore) {}
			sessionPlugin = sessionPlugins.get(requestMethod);
		}
		boolean contentDispositionMatters = request != null
			&& request.getContentDisposition() != null;
		String dispositionType = null;
		if (contentDispositionMatters) {
			dispositionType = request.getContentDisposition().getDispositionType();
		}
		boolean requestHasSdp = messageHasSdpOfInterest(request,
			contentDispositionMatters, dispositionType);
		boolean responseHasSdp = messageHasSdpOfInterest(response,
			contentDispositionMatters, dispositionType);
		boolean ackRequestHasSdp = messageHasSdpOfInterest(ackRequest,
			contentDispositionMatters, dispositionType);
		logger.debug("$ Messages: Req: {{}}, Res: {{}}, Ack: {{}}! $",
			requestHasSdp, responseHasSdp, ackRequestHasSdp);
		if (!requestHasSdp && !responseHasSdp && !ackRequestHasSdp) {
			//if UAC: OFFER at Request only
			//if UAS: OFFER at Response only
			return role == SipUserAgentRole.UAC
				? generateOffer(sessionPlugin, callId, request, contentDispositionMatters/*FIXME should be: true*/)
				: generateOffer(sessionPlugin, callId, response, contentDispositionMatters);
		} else if (requestHasSdp && !responseHasSdp && !ackRequestHasSdp) {
			//if UAC: ERROR -> no ANSWER at Response
			//if UAS: ANSWER at Response only
			return role == SipUserAgentRole.UAC ? false
				: generateAnswer(sessionPlugin, callId, type,
					request, response, contentDispositionMatters);
		} else if (!requestHasSdp && responseHasSdp && !ackRequestHasSdp) {
			//if UAC: ANSWER at AckRequest only
			//if UAS: ERROR -> no ANSWER at AckRequest
			return role == SipUserAgentRole.UAC
				? generateAnswer(sessionPlugin, callId, type,
					response, ackRequest, contentDispositionMatters) : false;
		} else if (requestHasSdp && responseHasSdp && !ackRequestHasSdp) {
			//if UAC: ANSWER at Response to my OFFER at Request has arrived
			//if UAS: just do nothing, since session was already established
			//when I sent my ANSWER at Response to OFFER at Request
			return role == SipUserAgentRole.UAC
				? receiveAnswerToAcceptedOffer(sessionPlugin, callId, type, response) : true;
		} else if (!requestHasSdp && responseHasSdp && ackRequestHasSdp) {
			//if UAC: just do nothing, since session was already established
			//when I sent my ANSWER at AckRequest to OFFER at Response
			//if UAS: ANSWER at AckRequest to my OFFER at Response has arrived
			return role == SipUserAgentRole.UAC ? true
				: receiveAnswerToAcceptedOffer(sessionPlugin, callId, type, ackRequest);
		} else if (requestHasSdp && responseHasSdp && ackRequestHasSdp) {
			//(X) SHOULD NEVER HAPPEN
			//if UAC: (X) since I already got ANSWER at Response to my OFFER at
			//Request, it makes no sense to me to have added ANSWER at AckRequest
			//if UAS: just do nothing, since session was already established
			//when I sent my ANSWER at Response to OFFER at Request
			return role == SipUserAgentRole.UAS;
		} else if (!requestHasSdp && !responseHasSdp && ackRequestHasSdp) {
			//(X) SHOULD NEVER HAPPEN
			//if UAC: (X) since sending OFFER in AckRequest is wrong
			//if UAS: (X) since it won't expect OFFER in AckRequest
			return false;
		} else if (requestHasSdp && !responseHasSdp && ackRequestHasSdp) {
			//(X) SHOULD NEVER HAPPEN
			//if UAC: (X) since sending OFFER in AckRequest is wrong
			//if UAS: (X) since it won't expect OFFER in AckRequest
			return false;
		}
		return false;
	}

	public boolean messageHasSdp(Message message, boolean withContentDisposition) {
		boolean messageHasSdp = messageHasSdpOfInterest(message, false, null);
		return withContentDisposition == (message.getContentDisposition() != null)
			&& messageHasSdp;
	}

	private boolean messageHasSdpOfInterest(Message message,
			boolean dispositionMatters, String dispositionType) {
		boolean messageHasContent = message != null && message.getContent() != null;
		ContentTypeHeader contentTypeHeader = null;
		if (message != null) {
			contentTypeHeader = (ContentTypeHeader) message
				.getHeader(ContentTypeHeader.NAME);
		}
		boolean contentIsSdp = contentTypeHeader != null
			&& contentTypeHeader.getContentType()
				.toLowerCase().trim().equals("application")
			&& contentTypeHeader.getContentSubType()
				.toLowerCase().trim().equals("sdp");
		boolean messageHasSdpOfInterest = messageHasContent && contentIsSdp;
		if (dispositionMatters && dispositionType != null
				&& !dispositionType.trim().isEmpty()) {
			messageHasSdpOfInterest &= message != null
				&& message.getContentDisposition().getDispositionType()
				.toLowerCase().trim().equals(dispositionType);
		}
		return messageHasSdpOfInterest;
	}

	private boolean generateOffer(SipuadaPlugin sessionPlugin,
			String callId, Message offerMessage, boolean dispositionMatters) {
		String offerMessageIdentifier = offerMessage instanceof Request
			? ((Request) offerMessage).getMethod()
			: Integer.toString(((Response) offerMessage).getStatusCode());
		if (sessionPlugin == null) {
			logger.info("No plug-in available to generate offer "
				+ "to be inserted into {}.", offerMessageIdentifier);
			return true;
		}
		SessionDescription offer = null;
		try {
			offer = sessionPlugin.generateOffer(callId, localAddress);
			logger.debug("* {} just generated offer \n{}\n to be inserted into {} in "
				+ "context of call {}! *", role, offer, offerMessageIdentifier, callId);
		} catch (Throwable unexpectedException) {
			logger.error("Bad plug-in crashed while {} tried generating offer " +
				"to be inserted into {} in context of call {}.", role,
				offerMessageIdentifier, callId, unexpectedException);
			return false;
		}
		if (offer == null) {
			logger.error("{}'s plug-in generated no offer to be inserted into {}"
				+ " in context of call {}.",
				role, offerMessageIdentifier, callId);
			return true;
		}
		try {
			offerMessage.setContent(offer, headerMaker
				.createContentTypeHeader("application", "sdp"));
			if (dispositionMatters) {
				offerMessage.setContentDisposition(headerMaker
					.createContentDispositionHeader(SessionType.EARLY.getDisposition()));
			}
			logger.info("{}'s plug-in-generated offer \n{}\n inserted into {}.",
				role, offer.toString(), offerMessageIdentifier);
			return true;
		} catch (ParseException parseException) {
			logger.error("{}'s plug-in-generated offer \n{}\n by could not "
				+ "be inserted into {}.", role, offer.toString(),
				offerMessageIdentifier, parseException);
			return false;
		}
	}

	private boolean generateAnswer(SipuadaPlugin sessionPlugin, String callId,
			SessionType type, Message offerMessage, Message answerMessage,
			boolean dispositionMatters) {
		String offerMessageIdentifier = offerMessage instanceof Request
			? ((Request) offerMessage).getMethod()
			: Integer.toString(((Response) offerMessage).getStatusCode());
		String answerMessageIdentifier = answerMessage instanceof Request
			? ((Request) answerMessage).getMethod()
			: Integer.toString(((Response) answerMessage).getStatusCode());
		if (sessionPlugin == null) {
			logger.info("No plug-in available to generate valid answer to offer in {},"
				+ " to be inserted into {}.", offerMessageIdentifier,
				answerMessageIdentifier);
			return false;
		}
		SessionDescription offer;
		try {
			offer = SdpFactoryImpl.getInstance().createSessionDescriptionFromString
				(new String(offerMessage.getRawContent()));
		} catch (SdpParseException parseException) {
			logger.error("Offer arrived in {}, but could not be properly parsed,"
				+ " so it was discarded.", offerMessageIdentifier, parseException);
			return sessionPlugin == null;
		}
		SessionDescription answer = null;
		try {
			answer = sessionPlugin.generateAnswer(callId, type, offer, localAddress);
			logger.debug("* {}'s plug-in just generated answer \n{}\n to offer \n{}\n in {},"
				+ " to be inserted into {} in context of call {}! *", role, answer, offer,
				offerMessageIdentifier, answerMessageIdentifier, callId);
		} catch (Throwable unexpectedException) {
			logger.error("Bad plug-in crashed while {} tried generating answer to offer"
				+ " \n{}\n in {}, to be inserted into {}. The {} will terminate the dialog "
				+ "right away.", role, offer.toString(), offerMessageIdentifier,
				answerMessageIdentifier, role, unexpectedException);
			return false;
		}
		if (answer == null) {
			logger.error("{}'s plug-in could not generate valid answer to offer \n{}\n in {},"
				+ " to be inserted into {}.", role, offer.toString(),
				offerMessageIdentifier, answerMessageIdentifier);
			return false;
		}
		try {
			answerMessage.setContent(answer, headerMaker
				.createContentTypeHeader("application", "sdp"));
			if (dispositionMatters) {
				answerMessage.setContentDisposition(headerMaker
					.createContentDispositionHeader(type.getDisposition()));
			}
		} catch (ParseException parseException) {
			logger.error("{}'s plug-in-generated answer \n{}\n to offer \n{}\n in {} "
				+ "could not be inserted into {}.", role, answer.toString(), offer.toString(),
				offerMessageIdentifier, answerMessageIdentifier, parseException);
			return false;
		}
		logger.info("{}'s plug-in-generated answer \n{}\n to offer \n{}\n "
			+ "in {} was inserted into {}.", role, answer.toString(), offer.toString(),
			offerMessageIdentifier, answerMessageIdentifier);
		return true;
	}

	private boolean receiveAnswerToAcceptedOffer(SipuadaPlugin sessionPlugin,
			String callId, SessionType type, Message answerMessage) {
		String answerMessageIdentifier = answerMessage instanceof Request
			? ((Request) answerMessage).getMethod()
			: Integer.toString(((Response) answerMessage).getStatusCode());
		try {
			if (sessionPlugin == null) {
				logger.info("No plug-in available to process valid answer that"
					+ " has arrived within {}.", answerMessageIdentifier);
				return false;
			}
			SessionDescription answer = SdpFactoryImpl.getInstance()
				.createSessionDescriptionFromString(new String(answerMessage.getRawContent()));
			try {
				logger.debug("{}'s plug-in will process answer \n{}\n in context"
					+ " of call {}!", role, answer.toString(), callId);
				sessionPlugin.receiveAnswerToAcceptedOffer(callId, type, answer);
				return true;
			} catch (Throwable unexpectedException) {
				logger.error("Bad plug-in crashed while {} received answer "
					+ "that arrived alongside {}. The {} will terminate "
					+ "the dialog right away.", role, answerMessageIdentifier,
					role, unexpectedException);
			}
		} catch (SdpParseException parseException) {
			logger.error("Answer arrived in {}, but could not be properly" +
				" parsed, so it was discarded. The {} will terminate the dialog right away.",
				answerMessageIdentifier, role, parseException);
		}
		return false;
	}

	public static boolean performSessionSetup(SipuadaPlugin sessionPlugin,
			String callId, SessionType sessionType, SipUserAgent sipUserAgent) {
		if (sessionPlugin == null) {
			return true;
		}
		boolean veredict = true;
		for (SessionType type : SessionType.values()) {
			if (sessionPlugin.isSessionOngoing(callId, type)) {
				veredict &= performSessionTermination(sessionPlugin, callId, type);
			}
		}
		return veredict && sessionPlugin.performSessionSetup
				(callId, sessionType, sipUserAgent);
	}

	public static boolean performSessionTermination(SipuadaPlugin sessionPlugin,
			String callId, SessionType sessionType) {
		if (sessionPlugin == null) {
			return true;
		}
		if (!sessionPlugin.isSessionOngoing(callId, sessionType)) {
			return true;
		}
		return sessionPlugin.performSessionTermination(callId, sessionType);
	}

}
