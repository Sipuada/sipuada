# Sipuada

## What is Sipuada?

A Java library for enabling applications support to the Session Initiation Protocol (SIP). Being fully RFC-3261 compliant, it's very useful for building SIP User Agents, which, alongside other SIP entities such as proxies and registrars, enables end-user apps to have real-time communication on a peer-to-peer basis. The SIP protocol is the same used by many VoIP clients, but is designed to support any kind of payload or data exchange. This library aims to support the session establishing layer only, and delegate specific session aspects to specific plugins (see [this link](https://github.com/Sipuada/sipuada-plugin-android-audio) if you want a sample. It's an audio plugin (supporting the PCMA and Speex codecs) specific to the Android platform).
