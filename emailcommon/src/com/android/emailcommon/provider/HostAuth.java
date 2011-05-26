/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.emailcommon.provider;

import com.android.emailcommon.provider.EmailContent.HostAuthColumns;
import com.android.emailcommon.utility.Utility;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.net.URI;
import java.net.URISyntaxException;

public final class HostAuth extends EmailContent implements HostAuthColumns, Parcelable {
    public static final String TABLE_NAME = "HostAuth";
    @SuppressWarnings("hiding")
    public static final Uri CONTENT_URI = Uri.parse(EmailContent.CONTENT_URI + "/hostauth");
    // TODO the three following constants duplicate constants in Store.java; remove those and
    //      just reference these.
    public static final String SCHEME_IMAP = "imap";
    public static final String SCHEME_POP3 = "pop3";
    public static final String SCHEME_EAS = "eas";
    public static final String SCHEME_SMTP = "smtp";

    public static final int PORT_UNKNOWN = -1;

    public static final int FLAG_NONE         = 0x00;    // No flags
    public static final int FLAG_SSL          = 0x01;    // Use SSL
    public static final int FLAG_TLS          = 0x02;    // Use TLS
    public static final int FLAG_AUTHENTICATE = 0x04;    // Use name/password for authentication
    public static final int FLAG_TRUST_ALL    = 0x08;    // Trust all certificates
    // Mask of settings directly configurable by the user
    public static final int USER_CONFIG_MASK  = 0x0b;

    public String mProtocol;
    public String mAddress;
    public int mPort;
    public int mFlags;
    public String mLogin;
    public String mPassword;
    public String mDomain;

    public static final int CONTENT_ID_COLUMN = 0;
    public static final int CONTENT_PROTOCOL_COLUMN = 1;
    public static final int CONTENT_ADDRESS_COLUMN = 2;
    public static final int CONTENT_PORT_COLUMN = 3;
    public static final int CONTENT_FLAGS_COLUMN = 4;
    public static final int CONTENT_LOGIN_COLUMN = 5;
    public static final int CONTENT_PASSWORD_COLUMN = 6;
    public static final int CONTENT_DOMAIN_COLUMN = 7;

    public static final String[] CONTENT_PROJECTION = new String[] {
        RECORD_ID, HostAuthColumns.PROTOCOL, HostAuthColumns.ADDRESS, HostAuthColumns.PORT,
        HostAuthColumns.FLAGS, HostAuthColumns.LOGIN,
        HostAuthColumns.PASSWORD, HostAuthColumns.DOMAIN
    };

    /**
     * no public constructor since this is a utility class
     */
    public HostAuth() {
        mBaseUri = CONTENT_URI;

        // other defaults policy)
        mPort = PORT_UNKNOWN;
    }

     /**
     * Restore a HostAuth from the database, given its unique id
     * @param context
     * @param id
     * @return the instantiated HostAuth
     */
    public static HostAuth restoreHostAuthWithId(Context context, long id) {
        return EmailContent.restoreContentWithId(context, HostAuth.class,
                HostAuth.CONTENT_URI, HostAuth.CONTENT_PROJECTION, id);
    }


    /**
     * Returns the scheme for the specified flags.
     */
    public static String getSchemeString(String protocol, int flags) {
        String security = "";
        switch (flags & USER_CONFIG_MASK) {
            case FLAG_SSL:
                security = "+ssl+";
                break;
            case FLAG_SSL | FLAG_TRUST_ALL:
                security = "+ssl+trustallcerts";
                break;
            case FLAG_TLS:
                security = "+tls+";
                break;
            case FLAG_TLS | FLAG_TRUST_ALL:
                security = "+tls+trustallcerts";
                break;
        }
        return protocol + security;
    }

    /**
     * Returns the flags for the specified scheme.
     */
    public static int getSchemeFlags(String scheme) {
        String[] schemeParts = scheme.split("\\+");
        int flags = HostAuth.FLAG_NONE;
        if (schemeParts.length >= 2) {
            String part1 = schemeParts[1];
            if ("ssl".equals(part1)) {
                flags |= HostAuth.FLAG_SSL;
            } else if ("tls".equals(part1)) {
                flags |= HostAuth.FLAG_TLS;
            }
            if (schemeParts.length >= 3) {
                String part2 = schemeParts[2];
                if ("trustallcerts".equals(part2)) {
                    flags |= HostAuth.FLAG_TRUST_ALL;
                }
            }
        }
        return flags;
    }

    @Override
    public void restore(Cursor cursor) {
        mBaseUri = CONTENT_URI;
        mId = cursor.getLong(CONTENT_ID_COLUMN);
        mProtocol = cursor.getString(CONTENT_PROTOCOL_COLUMN);
        mAddress = cursor.getString(CONTENT_ADDRESS_COLUMN);
        mPort = cursor.getInt(CONTENT_PORT_COLUMN);
        mFlags = cursor.getInt(CONTENT_FLAGS_COLUMN);
        mLogin = cursor.getString(CONTENT_LOGIN_COLUMN);
        mPassword = cursor.getString(CONTENT_PASSWORD_COLUMN);
        mDomain = cursor.getString(CONTENT_DOMAIN_COLUMN);
    }

    @Override
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(HostAuthColumns.PROTOCOL, mProtocol);
        values.put(HostAuthColumns.ADDRESS, mAddress);
        values.put(HostAuthColumns.PORT, mPort);
        values.put(HostAuthColumns.FLAGS, mFlags);
        values.put(HostAuthColumns.LOGIN, mLogin);
        values.put(HostAuthColumns.PASSWORD, mPassword);
        values.put(HostAuthColumns.DOMAIN, mDomain);
        values.put(HostAuthColumns.ACCOUNT_KEY, 0); // Need something to satisfy the DB
        return values;
    }

    /**
     * For compatibility while converting to provider model, generate a "store URI"
     * TODO cache this so we don't rebuild every time
     *
     * @return a string in the form of a Uri, as used by the other parts of the email app
     */
    public String getStoreUri() {
        String userInfo = null;
        if ((mFlags & FLAG_AUTHENTICATE) != 0) {
            String trimUser = (mLogin != null) ? mLogin.trim() : "";
            String password = (mPassword != null) ? mPassword : "";
            userInfo = trimUser + ":" + password;
        }
        String scheme = getSchemeString(mProtocol, mFlags);
        String address = (mAddress != null) ? mAddress.trim() : null;
        String path = (mDomain != null) ? "/" + mDomain : null;

        URI uri;
        try {
            uri = new URI(
                    scheme,
                    userInfo,
                    address,
                    mPort,
                    path,
                    null,
                    null);
            return uri.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Sets the user name and password from URI user info string
     */
    public void setLogin(String userInfo) {
        String userName = null;
        String userPassword = null;
        if (!TextUtils.isEmpty(userInfo)) {
            String[] userInfoParts = userInfo.split(":", 2);
            userName = userInfoParts[0];
            if (userInfoParts.length > 1) {
                userPassword = userInfoParts[1];
            }
        }
        setLogin(userName, userPassword);
    }

    /**
     * Sets the user name and password
     */
    public void setLogin(String userName, String userPassword) {
        mLogin = userName;
        mPassword = userPassword;

        if (mLogin == null) {
            mFlags &= ~FLAG_AUTHENTICATE;
        } else {
            mFlags |= FLAG_AUTHENTICATE;
        }
    }

    /**
     * Returns the login information. [0] is the username and [1] is the password. If
     * {@link #FLAG_AUTHENTICATE} is not set, {@code null} is returned.
     */
    public String[] getLogin() {
        if ((mFlags & FLAG_AUTHENTICATE) != 0) {
            String trimUser = (mLogin != null) ? mLogin.trim() : "";
            String password = (mPassword != null) ? mPassword : "";
            return new String[] { trimUser, password };
        }
        return null;
    }

    /**
     * Sets the connection values of the auth structure per the given scheme, host and port.
     */
    public void setConnection(String scheme, String host, int port) {
        String[] schemeParts = scheme.split("\\+");
        String protocol = schemeParts[0];
        int flags = getSchemeFlags(scheme);

        setConnection(protocol, host, port, flags);
    }

    public void setConnection(String protocol, String address, int port, int flags) {
        // Set protocol, security, and additional flags based on uri scheme
        mProtocol = protocol;

        mFlags &= ~(FLAG_SSL | FLAG_TLS | FLAG_TRUST_ALL);
        mFlags |= (flags & USER_CONFIG_MASK);

        mAddress = address;
        mPort = port;
        if (mPort == PORT_UNKNOWN) {
            boolean useSSL = ((mFlags & FLAG_SSL) != 0);
            // infer port# from protocol + security
            // SSL implies a different port - TLS runs in the "regular" port
            // NOTE: Although the port should be setup in the various setup screens, this
            // block cannot easily be moved because we get process URIs from other sources
            // (e.g. for tests, provider templates and account restore) that may or may not
            // have a port specified.
            if (SCHEME_POP3.equals(mProtocol)) {
                mPort = useSSL ? 995 : 110;
            } else if (SCHEME_IMAP.equals(mProtocol)) {
                mPort = useSSL ? 993 : 143;
            } else if (SCHEME_EAS.equals(mProtocol)) {
                mPort = useSSL ? 443 : 80;
            } else if (SCHEME_SMTP.equals(mProtocol)) {
                mPort = useSSL ? 465 : 587;
            }
        }
    }

    /** Returns {@code true} if this is an EAS connection; otherwise, {@code false}. */
    public boolean isEasConnection() {
        return SCHEME_EAS.equals(mProtocol);
    }

    /**
     * Supports Parcelable
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Supports Parcelable
     */
    public static final Parcelable.Creator<HostAuth> CREATOR
            = new Parcelable.Creator<HostAuth>() {
        @Override
        public HostAuth createFromParcel(Parcel in) {
            return new HostAuth(in);
        }

        @Override
        public HostAuth[] newArray(int size) {
            return new HostAuth[size];
        }
    };

    /**
     * Supports Parcelable
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // mBaseUri is not parceled
        dest.writeLong(mId);
        dest.writeString(mProtocol);
        dest.writeString(mAddress);
        dest.writeInt(mPort);
        dest.writeInt(mFlags);
        dest.writeString(mLogin);
        dest.writeString(mPassword);
        dest.writeString(mDomain);
    }

    /**
     * Supports Parcelable
     */
    public HostAuth(Parcel in) {
        mBaseUri = CONTENT_URI;
        mId = in.readLong();
        mProtocol = in.readString();
        mAddress = in.readString();
        mPort = in.readInt();
        mFlags = in.readInt();
        mLogin = in.readString();
        mPassword = in.readString();
        mDomain = in.readString();
    }

    /**
     * For debugger support only - DO NOT use for code.
     */
    @Override
    public String toString() {
        return getStoreUri();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HostAuth)) {
            return false;
        }
        HostAuth that = (HostAuth)o;
        return mPort == that.mPort
                && mFlags == that.mFlags
                && Utility.areStringsEqual(mProtocol, that.mProtocol)
                && Utility.areStringsEqual(mAddress, that.mAddress)
                && Utility.areStringsEqual(mLogin, that.mLogin)
                && Utility.areStringsEqual(mPassword, that.mPassword)
                && Utility.areStringsEqual(mDomain, that.mDomain);
    }
}