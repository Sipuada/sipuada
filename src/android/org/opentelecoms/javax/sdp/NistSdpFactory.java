/**
 * Copyright (C) 2016, The Open Telecoms Project, http://opentelecoms.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.org.opentelecoms.javax.sdp;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import android.gov.nist.javax.sdp.MediaDescriptionImpl;
import android.gov.nist.javax.sdp.SessionDescriptionImpl;
import android.gov.nist.javax.sdp.TimeDescriptionImpl;
import android.gov.nist.javax.sdp.fields.AttributeField;
import android.gov.nist.javax.sdp.fields.BandwidthField;
import android.gov.nist.javax.sdp.fields.ConnectionField;
import android.gov.nist.javax.sdp.fields.EmailField;
import android.gov.nist.javax.sdp.fields.InformationField;
import android.gov.nist.javax.sdp.fields.KeyField;
import android.gov.nist.javax.sdp.fields.MediaField;
import android.gov.nist.javax.sdp.fields.OriginField;
import android.gov.nist.javax.sdp.fields.PhoneField;
import android.gov.nist.javax.sdp.fields.ProtoVersionField;
import android.gov.nist.javax.sdp.fields.RepeatField;
import android.gov.nist.javax.sdp.fields.SessionNameField;
import android.gov.nist.javax.sdp.fields.TimeField;
import android.gov.nist.javax.sdp.fields.URIField;
import android.gov.nist.javax.sdp.fields.ZoneField;
import android.gov.nist.javax.sdp.parser.SDPAnnounceParser;
import android.javax.sdp.Attribute;
import android.javax.sdp.BandWidth;
import android.javax.sdp.Connection;
import android.javax.sdp.EMail;
import android.javax.sdp.Info;
import android.javax.sdp.Key;
import android.javax.sdp.Media;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.Origin;
import android.javax.sdp.Phone;
import android.javax.sdp.RepeatTime;
import android.javax.sdp.SdpConstants;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
import android.javax.sdp.SessionName;
import android.javax.sdp.Time;
import android.javax.sdp.TimeDescription;
import android.javax.sdp.TimeZoneAdjustment;
import android.javax.sdp.URI;
import android.javax.sdp.Version;

/**
 * SdpFactory that creates instances of the NIST implementation.
 * 
 * @author Ingo Bauersachs
 */
public class NistSdpFactory implements SdpFactory {

    public SessionDescription createSessionDescription() throws SdpException {
        SessionDescriptionImpl sd = new SessionDescriptionImpl();
        sd.setVersion(this.createVersion(0));

        try {
            sd.setOrigin(this.createOrigin("user", InetAddress.getLocalHost().toString()));
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }

        sd.setSessionName(this.createSessionName("-"));
        Vector<TimeDescription> times = new Vector<TimeDescription>();
        TimeField tf = new TimeField();
        tf.setZero();
        times.addElement(new TimeDescriptionImpl(tf));
        sd.setTimeDescriptions(times);
        return sd;
    }

    public SessionDescription createSessionDescription(String s) throws SdpParseException {
        SDPAnnounceParser sap = new SDPAnnounceParser(s);
        try {
            return sap.parse();
        }
        catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BandWidth createBandwidth(String modifier, int value) {
        BandwidthField bw = new BandwidthField();
        try {
            bw.setBandwidth(value);
            bw.setType(modifier);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return bw;
    }

    public Attribute createAttribute(String name, String value) {
        AttributeField a = new AttributeField();
        try {
            a.setName(name);
            a.setValueAllowNull(value);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return a;
    }

    public Info createInfo(String value) {
        InformationField i = new InformationField();
        try {
            i.setValue(value);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return i;
    }

    public Phone createPhone(String value) {
        PhoneField p = new PhoneField();
        try {
            p.setValue(value);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return p;
    }

    public EMail createEMail(String value) {
        EmailField e = new EmailField();
        try {
            e.setValue(value);
        }
        catch (SdpException e1) {
            e1.printStackTrace();
            return null;
        }

        return e;
    }

    public URI createURI(URL value) throws SdpException {
        URI u = new URIField();
        u.set(value);
        return u;
    }

    public SessionName createSessionName(String name) {
        SessionNameField sn = new SessionNameField();
        try {
            sn.setValue(name);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return sn;
    }

    public Key createKey(String method, String key) {
        KeyField k = new KeyField();
        try {
            k.setMethod(method);
            k.setKey(key);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return k;
    }

    public Version createVersion(int value) {
        ProtoVersionField pv = new ProtoVersionField();
        try {
            pv.setVersion(value);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return pv;
    }

    public Media createMedia(String media, int port, int numPorts, String transport,
            @SuppressWarnings("rawtypes") Vector staticRtpAvpTypes) throws SdpException {
        MediaField m = new MediaField();
        m.setMediaType(media);
        m.setMediaPort(port);
        m.setPortCount(numPorts);
        m.setProtocol(transport);
        m.setMediaFormats(staticRtpAvpTypes);
        return m;
    }

    public Origin createOrigin(String userName, String address) throws SdpException {
        OriginField o = new OriginField();
        o.setUsername(userName);
        o.setAddress(address);
        return o;
    }

    public Origin createOrigin(String userName, long sessionId, long sessionVersion,
            String networkType, String addrType, String address) throws SdpException {
        OriginField o = new OriginField();
        o.setUsername(userName);
        o.setSessionId(sessionId);
        o.setSessionVersion(sessionVersion);
        o.setNetworkType(networkType);
        return o;
    }

    public MediaDescription createMediaDescription(String media, int port,
            int numPorts, String transport, int[] staticRtpAvpTypes)
                    throws IllegalArgumentException, SdpException {
        MediaDescriptionImpl md = new MediaDescriptionImpl();
        Vector<String> types = new Vector<String>();
        for (int type : staticRtpAvpTypes) {
            types.addElement(new Integer(type).toString());
        }

        Media m = createMedia(media, port, numPorts, transport, types);
        md.setMedia(m);
        return md;
    }

    public MediaDescription createMediaDescription(String media, int port, int numPorts, String transport, String[] formats) {
        MediaDescriptionImpl md = new MediaDescriptionImpl();
        Vector<String> types = new Vector<String>();
        for (String format : formats) {
            types.addElement(format);
        }

        Media m;
        try {
            m = createMedia(media, port, numPorts, transport, types);
            md.setMedia(m);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return md;
    }

    public TimeDescription createTimeDescription(Time t) throws SdpException {
        TimeDescription td = createTimeDescription();
        td.setTime(t);
        return td;
    }

    public TimeDescription createTimeDescription() throws SdpException {
        return new TimeDescriptionImpl(); 
    }

    public TimeDescription createTimeDescription(Date start, Date stop) throws SdpException {
        TimeDescription td = createTimeDescription();
        td.setTime(createTime(start, stop));
        return td;
    }

    public String formatMulticastAddress(String addr, int ttl, int numAddrs) {
        return new StringBuilder()
                .append(addr).append('/')
                .append(ttl).append('/')
                .append(numAddrs).toString();
    }


    /**
     * @param ttl ignored
     * @param numAddrs ignored
     */
    public Connection createConnection(String netType, String addrType, String addr, int ttl, int numAddrs) throws SdpException {
        Connection c = createConnection(netType, addrType, addr);
        return c;
    }

    public Connection createConnection(String netType, String addrType, String addr) throws SdpException {
        Connection c = createConnection(addr);
        c.setNetworkType(netType);
        c.setAddressType(addrType);
        return c;
    }

    /**
     * @param ttl ignored
     * @param numAddrs ignored
     */
    public Connection createConnection(String addr, int ttl, int numAddrs) throws SdpException {
        return createConnection(addr);
    }

    public Connection createConnection(String addr) throws SdpException {
        Connection c = new ConnectionField();
        c.setAddress(addr);
        return c;
    }

    public Time createTime(Date start, Date stop) throws SdpException {
        Time t = createTime();
        t.setStart(start);
        t.setStop(stop);
        return t;
    }

    public Time createTime() throws SdpException {
        return new TimeField();
    }

    public RepeatTime createRepeatTime(int repeatInterval, int activeDuration, int[] offsets) {
        RepeatField rt = new RepeatField();
        try {
            rt.setRepeatInterval(repeatInterval);
            rt.setActiveDuration(activeDuration);
            rt.setOffsetArray(offsets);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return rt;
    }

    public TimeZoneAdjustment createTimeZoneAdjustment(Date d, int offset) {
        ZoneField z = new ZoneField();
        Hashtable<Date, Integer> t = new Hashtable<Date, Integer>(1);
        t.put(d, offset);
        try {
            z.setZoneAdjustments(t);
        }
        catch (SdpException e) {
            e.printStackTrace();
            return null;
        }

        return z;
    }

    public Date getDateFromNtp(long ntpTime) {
        return new Date((ntpTime - SdpConstants.NTP_CONST) * 1000);
    }

    public long getNtpTime(Date d) throws SdpParseException {
        if (d == null) {
            return -1;
        }

        return ((d.getTime() / 1000) + SdpConstants.NTP_CONST);
    }
}
