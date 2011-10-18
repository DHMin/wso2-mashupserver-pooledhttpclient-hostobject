/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.mashup.javascript.hostobjects.pooledhttpclient;

import java.util.Hashtable;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;

/**
 * @author Frederick Haebin Na
 */
public class HttpCommand implements Runnable {
    private HttpClient client = null;
    private HttpMethod method = null;
    private Hashtable<String, SimpleHttpResponse> result = null;
    private int resultIndex = -1;

    public HttpCommand(HttpClient client, HttpMethod method,
	    Hashtable<String, SimpleHttpResponse> result, int resultIndex) {
	this.client = client;
	this.method = method;
	this.result = result;
	this.resultIndex = resultIndex;
    }

    public void run() {
	try {
	    client.executeMethod(method);
	    result.put(resultIndex + "", toResponse(method));
	} catch (Exception e) {
	    result.put(resultIndex + "",
		    new SimpleHttpResponse(600, e.getMessage() + "\r\n"
			    + e.getCause().toString(), null, null));
	}
    }

    private SimpleHttpResponse toResponse(HttpMethod method) {
	SimpleHttpResponse response;
	Header[] headers = method.getResponseHeaders();
	String headersString = "";
	for (Header header : headers) {
	    headersString += header.toString();
	}
	try {
	    response = new SimpleHttpResponse(method.getStatusCode(),
		    method.getStatusText(), headersString,
		    method.getResponseBodyAsString());
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
	return response;
    }
}
