/*
 * @(#)SimpleHttpRespone.java $version 2011. 9. 30.
 *
 * Copyright 2007 NHN Corp. All rights Reserved. 
 * NHN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.wso2.carbon.mashup.javascript.hostobjects.pooledhttpclient;

import org.apache.commons.httpclient.Header;

/**
 * @author Frederick Haebin Na
 */
public class SimpleHttpResponse {
    public String statusCode;
    public String statusText;
    public String headers;
    public String response;
}