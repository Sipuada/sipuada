package org.github.sipuada.state;

import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.requester.SipRequestVerb;
import org.github.sipuada.requester.SipResponseCode;
import org.github.sipuada.state.SipStateMachineBehavior.During;
import org.github.sipuada.state.SipStateMachineBehavior.Step;

import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipStateMachine extends AbstractSipStateMachine {

	private State currentState;
	private SipStateMachineBehavior currentBehavior;

	public SipStateMachine() {
		currentState = State.IDLE;
		currentBehavior = new SipStateMachineBehavior();

		whileIn(State.IDLE).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.REGISTER)
			.goTo(State.REGISTERING).andAllowThisOutgoingRequest();
		whileIn(State.IDLE).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.INCOMING).thenSendResponse(SipResponseCode.RINGING);
		whileIn(State.IDLE).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.MESSAGE)
			.goTo(State.READY).thenSendResponse(SipResponseCode.OK);
		whileIn(State.IDLE).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.IDLE).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.IDLE).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.IDLE).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.IDLE).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.IDLE).thenSendResponse(SipResponseCode.BAD_REQUEST);

		whileIn(State.REGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.INCOMING).thenSendResponse(SipResponseCode.RINGING);
		whileIn(State.REGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.MESSAGE)
			.goTo(State.READY).thenSendResponse(SipResponseCode.OK);
		whileIn(State.REGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.REGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.REGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.REGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.REGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.REGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.REGISTERING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.READY);
		whileIn(State.REGISTERING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.PROXY_AUTHENTICATION_REQUIRED)
			.goTo(State.REGISTERING).thenSendFollowUpRequest(SipRequestVerb.REGISTER);
		whileIn(State.REGISTERING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.UNAUTHORIZED)
			.goTo(State.REGISTERING).thenSendFollowUpRequest(SipRequestVerb.REGISTER);

		whileIn(State.READY).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.UNREGISTER)
			.goTo(State.UNREGISTERING).andAllowThisOutgoingRequest();
		whileIn(State.READY).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.INVITE)
			.goTo(State.CALLING).andAllowThisOutgoingRequest();
		whileIn(State.READY).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.INCOMING).thenSendResponse(SipResponseCode.RINGING);
		whileIn(State.READY).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.READY).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.READY).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.READY).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.READY).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.READY).thenSendResponse(SipResponseCode.BAD_REQUEST);

		whileIn(State.UNREGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.UNREGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.UNREGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.MESSAGE)
			.goTo(State.UNREGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.UNREGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.UNREGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.UNREGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.UNREGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.UNREGISTERING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.UNREGISTERING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.UNREGISTERING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.IDLE);
		
		whileIn(State.CALLING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.CALLING).thenSendResponse(SipResponseCode.BUSY_HERE);
		whileIn(State.CALLING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.CALLING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.CALLING).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.CANCEL)
			.goTo(State.FINISHED).andAllowThisOutgoingRequest();
		whileIn(State.CALLING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.CALLING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.CALLING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.CALLING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.ESTABLISHED);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.RINGING)
			.goTo(State.RINGING);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.PROXY_AUTHENTICATION_REQUIRED)
			.goTo(State.CALLING).thenSendFollowUpRequest(SipRequestVerb.INVITE);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.UNAUTHORIZED)
			.goTo(State.CALLING).thenSendFollowUpRequest(SipRequestVerb.INVITE);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.BUSY_HERE)
			.goTo(State.READY);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.REQUEST_TIMEOUT)
			.goTo(State.READY);
		whileIn(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.NOT_FOUND)
			.goTo(State.READY);

		whileIn(State.RINGING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.RINGING).thenSendResponse(SipResponseCode.BUSY_HERE);
		whileIn(State.RINGING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.RINGING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.RINGING).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.CANCEL)
			.goTo(State.FINISHED).andAllowThisOutgoingRequest();
		whileIn(State.RINGING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.RINGING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.RINGING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.RINGING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.RINGING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.ESTABLISHED);
		whileIn(State.RINGING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.DECLINE)
			.goTo(State.FINISHED);

		whileIn(State.INCOMING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.INCOMING).thenSendResponse(SipResponseCode.BUSY_HERE);
		whileIn(State.INCOMING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.INCOMING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.INCOMING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.FINISHED).thenSendResponse(SipResponseCode.OK);
		whileIn(State.INCOMING).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.INCOMING).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.INCOMING).whenResponse(MessageDirection.OUTGOING).with(SipResponseCode.OK)
			.goTo(State.ESTABLISHED);
		whileIn(State.INCOMING).whenResponse(MessageDirection.OUTGOING).with(SipResponseCode.DECLINE)
			.goTo(State.FINISHED);

		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.ESTABLISHED).thenSendResponse(SipResponseCode.BUSY_HERE);
		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.INFO)
			.goTo(State.ESTABLISHED).andAllowThisOutgoingRequest();
		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.ESTABLISHED).thenSendResponse(SipResponseCode.OK);
		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.ESTABLISHED).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.BYE)
			.goTo(State.FINISHED).andAllowThisOutgoingRequest();
		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.FINISHED).thenSendResponse(SipResponseCode.OK);

		whileIn(State.FINISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.FINISHED).thenSendResponse(SipResponseCode.BUSY_HERE);
		whileIn(State.FINISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INFO)
			.goTo(State.FINISHED).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.FINISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.CANCEL)
			.goTo(State.FINISHED).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.FINISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.FINISHED).thenSendResponse(SipResponseCode.BAD_REQUEST);
		whileIn(State.FINISHED).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.READY);
	}

	@Override
	public State getState() {
		return currentState;
	}

	/**
	 * Computes next step in the state machine by feeding it with new incoming/outgoing request.
	 * @return true if the machine compute a new state and, if the desired request is outgoing, if it is allowed, false otherwise
	 */
	@Override
	protected boolean handleRequestComputingNextStep(MessageDirection direction, SipRequestVerb requestVerb, Request request) {
		Step nextStep = currentBehavior.computeNextStepAfterRequest(currentState, direction, requestVerb);
		if (nextStep == null) {
			return false;
		}
		currentState = nextStep.getNextState();
		if (nextStep.hasFollowUpResponse()) {
			responseMustBeSent(new SendResponseEvent(nextStep.getFollowUpResponseCode(), request));
		}
		return direction == MessageDirection.OUTGOING ? nextStep.outgoingRequestIsAllowed() : true;
	}

	/**
	 * Computes next step in the state machine by feeding it with new incoming/outgoing response.
	 * @return true if the machine compute a new state, false otherwise
	 */
	@Override
	protected boolean handleResponseComputingNextStep(MessageDirection direction, int responseCode, Response response) {
		Step nextStep = currentBehavior.computeNextStepAfterResponse(currentState, direction, responseCode);
		if (nextStep == null) {
			return false;
		}
		currentState = nextStep.getNextState();
		if (nextStep.hasFollowUpRequest()) {
			requestMustBeSent(new SendRequestEvent(nextStep.getFollowUpRequestVerb(), response));
		}
		return true;
	}

	public During whileIn(State currentState) {
		return currentBehavior.during(currentState);
	}

}
