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

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.UniqueTag;

/**
 * <p/>
 * This is a JavaScript Rhino host object aimed to provide a set of functions to
 * do HTTP POST/GET for the javascript service developers. WSO2 version of
 * httpclient did not provide concurrency, yet this one does.
 * </p>
 * 
 * @author WSO2
 * @author Frederick Haebin Na
 */
public class PooledHttpClientHostObject extends ScriptableObject {

    /**
     * 
     */
    private static final long serialVersionUID = 8681818519236634437L;

    public static final int CONN_POOL_THREAD_MAX = 10;
    public static final int CONN_POOL_GLOBAL_MAX = CONN_POOL_THREAD_MAX * 30;
    public static final int THREAD_POOL_MAX = 100;

    private HttpClient httpClient;

    private NativeArray authSchemePriority = null;
    private NativeArray cookies = null;
    private NativeObject credentials = null;
    private NativeObject proxyCredentials = null;
    private NativeObject host = null;

    private static final String DEFAULT_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String DEFAULT_CHARSET = "ISO-8859-1";

    private static final String DEFAULT_HOST_PROTOCOL = "http";
    private static final int DEFAULT_HOST_PORT = 80;

    private static final Object AGE = null;
    private static final String PATH = "/";
    private static final Boolean SECURE = false;

    private static final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(
	    THREAD_POOL_MAX);
    private static final ThreadPoolExecutor pool = new ThreadPoolExecutor(
	    THREAD_POOL_MAX, THREAD_POOL_MAX, 180, TimeUnit.SECONDS, queue);

    public PooledHttpClientHostObject() {
	MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
	HttpConnectionManagerParams params = connectionManager.getParams();
	params.setDefaultMaxConnectionsPerHost(CONN_POOL_THREAD_MAX);
	params.setMaxTotalConnections(CONN_POOL_GLOBAL_MAX);

	this.httpClient = new HttpClient(connectionManager);
    }

    /**
     * Type to be used for this object inside the javascript.
     */
    public String getClassName() {
	return "PooledHttpClient";
    }

    /**
     * Constructor the user will be using inside javaScript, this doesn't need
     * any arguments to be passed
     */
    public static Scriptable jsConstructor(Context cx, Object[] args,
	    Function ctorObj, boolean inNewExpr) {
	cx.setWrapFactory(new PrimitiveWrapFactory());
	PooledHttpClientHostObject httpClient = new PooledHttpClientHostObject();
	if (args.length != 0) {
	    throw new RuntimeException(
		    "HttpClient constructor doesn't accept any arguments");
	}
	return httpClient;
    }

    /**
     * <p/>
     * This method executes the given HTTP method with user configured
     * details.ject to invoke a Web service. This method corresponds to the
     * following function of the HttpClient java script object.
     * </p>
     * <p/>
     * 
     * <pre>
     *    int executeMethod (
     *          in String method | in String url [, in String/Object content [, in Object params [,
     * Object headers]]]);
     */
    public static Scriptable jsFunction_executeMethod(Context cx,
	    Scriptable thisObj, Object[] args, Function funObj) {

	HttpMethod method = null;
	PooledHttpClientHostObject httpClientHostObject = (PooledHttpClientHostObject) thisObj;

	List<String> authSchemes = new ArrayList<String>();

	String contentType = PooledHttpClientHostObject.DEFAULT_CONTENT_TYPE;
	String charset = PooledHttpClientHostObject.DEFAULT_CHARSET;

	/*
	 * we check weather authSchemes Priority has been set and put them into
	 * a List
	 */
	if (httpClientHostObject.authSchemePriority != null) {
	    setAuthSchemes(httpClientHostObject, authSchemes);
	}

	if (httpClientHostObject.credentials != null) {
	    setCredentials(httpClientHostObject, authSchemes);
	}

	if (httpClientHostObject.proxyCredentials != null) {
	    setProxyCredentials(httpClientHostObject, authSchemes);
	}

	// checks whether cookies have been set
	if (httpClientHostObject.cookies != null) {
	    setCookies(httpClientHostObject);
	}

	String methodName = null;
	// String url = null;
	NativeArray urls = null;
	Object content = null;
	NativeObject params = null;
	NativeArray headers = null;

	if (args[0] instanceof String) {
	    methodName = (String) args[0];
	} else {
	    throw new RuntimeException("HTTP method should be a String value");
	}

	if (args[1] instanceof String) {
	    Object[] tmp = { args[1] };
	    urls = (NativeArray) cx.newArray(thisObj, tmp);
	} else if (args[1] instanceof NativeArray) {
	    urls = (NativeArray) args[1];
	} else {
	    throw new RuntimeException(
		    "Url should be a String value or Array of Strings");
	}

	if (args.length > 2) {
	    if (args[2] instanceof String) {
		content = (String) args[2];
	    } else if (args[2] instanceof NativeArray) {
		content = (NativeArray) args[2];
	    } else if (args[2] != null) {
		throw new RuntimeException(
			"Content should be a String value or Array of Name-value pairs");
	    }
	}

	if (args.length > 3) {
	    if (args[3] instanceof NativeObject) {
		params = (NativeObject) args[3];
	    } else if (args[3] != null) {
		throw new RuntimeException(
			"Params argument should be an Object");
	    }
	}

	if (args.length > 4) {
	    if (args[4] instanceof NativeArray) {
		headers = (NativeArray) args[4];
	    } else if (args[4] != null) {
		throw new RuntimeException(
			"Headers argument should be an Object");
	    }
	}

	Hashtable<String, SimpleHttpResponse> result = new Hashtable<String, SimpleHttpResponse>();
	int i = 0;
	for (i = 0; i < urls.getLength(); i++) {
	    String url = urls.get(i, urls).toString();

	    if (url != null) {
		if (methodName.equals("GET")) {
		    method = new GetMethod(url);
		} else if (methodName.equals("POST")) {
		    method = new PostMethod(url);
		} else if (methodName.equals("PUT")) {
		    method = new PutMethod(url);
		} else if (methodName.equals("DELETE")) {
		    method = new DeleteMethod(url);
		} else {
		    throw new RuntimeException(
			    "HTTP method you specified is not supported");
		}
	    } else {
		throw new RuntimeException("A url should be specified");
	    }

	    if (headers != null) {
		setHeaders(method, headers);
	    }

	    if (params != null) {
		setParams(httpClientHostObject, method, contentType, charset,
			methodName, content, params);
	    } else if (content != null) {
		setContent(method, contentType, charset, methodName, content);
	    }

	    // check whether host configuration details has been set
	    if (httpClientHostObject.host != null) {
		setHostConfig(httpClientHostObject);
	    }

	    pool.execute(new HttpCommand(httpClientHostObject.httpClient,
		    method, result, i));
	}

	// wait for the result.
	int total = i;
	do {
	    try {
		Thread.sleep(100);
	    } catch (InterruptedException e) {
		throw new RuntimeException(e);
	    }
	} while (result.size() < total);

	if (result.size() == 1) {
	    return Context.toObject(result.get("0"), thisObj);
	} else {
	    // order matters
	    Object[] res = new Object[result.size()];
	    for (i = 0; i < result.size(); i++) {
		res[i] = result.get(i + "");
	    }
	    return Context.toObject(res, thisObj);
	}
    }

    /**
     * <p>
     * This property sets the authSchemePriority of the HttpClient's
     * authentications schemes
     * </p>
     * 
     * <pre>
     * httpClient.authSchemePriority =
     * ["NTLM", "BASIC", "DIGEST"];
     * </pre>
     */
    public void jsSet_authSchemePriority(Object object) {
	// sets authentication scheme priority of apache httpclient
	if (object instanceof NativeArray) {
	    this.authSchemePriority = (NativeArray) object;
	} else {
	    throw new RuntimeException(
		    "HttpClient Authentication Scheme Priority should be an Array");
	}
    }

    /**
     * <p>
     * This property sets cookies of the HttpClient
     * </p>
     * 
     * <pre>
     * httpClient.cookies = [ { domain : ".wso2.com", name : "myCookie", value :
     * "ADCFE113450593", path : "/", age : 20000, secure : true}, ..... ..... ];
     * </pre>
     */
    public void jsSet_cookies(Object object) {
	// sets authentication scheme priority of apache httpclient
	if (object instanceof NativeArray) {
	    this.cookies = (NativeArray) object;
	} else {
	    throw new RuntimeException("HttpClient Cookies should be an Array");
	}
    }

    /**
     * <p>
     * This property sets credentials of the HttpClient
     * </p>
     * 
     * <pre>
     * httpClient.credentials = { scope : { host : "www.wso2.com", port : 80,
     * realm : "web", scheme : "basic"}, credentials : { username : "ruchira",
     * password : "ruchira"} };
     * </pre>
     */
    public void jsSet_credentials(Object object) {
	// sets authentication scheme priority of apache httpclient
	if (object instanceof NativeObject) {
	    this.credentials = (NativeObject) object;
	} else {
	    throw new RuntimeException(
		    "HttpClient Credentials should be an Object");
	}
    }

    /**
     * <p>
     * This property sets credentials of the HttpClient
     * </p>
     * 
     * <pre>
     * httpClient.credentials = { scope : { host : "www.wso2.com", port : 80,
     * realm : "web", scheme : "basic"}, credentials : { username : "ruchira",
     * password : "ruchira"} };
     * </pre>
     */
    public void jsSet_proxyCredentials(Object object) {
	// sets authentication scheme priority of apache httpclient
	if (object instanceof NativeObject) {
	    this.proxyCredentials = (NativeObject) object;
	    // httpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,
	    // nativeAuthSchemePriority);
	} else {
	    throw new RuntimeException(
		    "HttpClient Proxy Credentials should be an Object");
	}
    }

    /**
     * <p>
     * This property sets host configurations of the HttpClient
     * </p>
     * 
     * <pre>
     * httpClient.host = { host : "www.wso2.com", port : 80, protocol :
     * "https"};
     * </pre>
     */
    public void jsSet_host(Object object) {
	// sets authentication scheme priority of apache httpclient
	if (object instanceof NativeObject) {
	    this.host = (NativeObject) object;
	} else {
	    throw new RuntimeException("HttpClient Host should be an Object");
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     */
    private static void setHostConfig(PooledHttpClientHostObject httpClient) {
	String host;
	int port = PooledHttpClientHostObject.DEFAULT_HOST_PORT;
	String protocol = PooledHttpClientHostObject.DEFAULT_HOST_PROTOCOL;

	if (ScriptableObject.getProperty(httpClient.host, "host") instanceof String) {
	    host = (String) ScriptableObject.getProperty(httpClient.host,
		    "host");
	} else {
	    throw new RuntimeException("Host property should be a String");
	}

	if (ScriptableObject.getProperty(httpClient.host, "port") instanceof Integer) {
	    port = (Integer) ScriptableObject.getProperty(httpClient.host,
		    "port");
	}

	if (ScriptableObject.getProperty(httpClient.host, "protocol") instanceof String) {
	    protocol = (String) ScriptableObject.getProperty(httpClient.host,
		    "protocol");
	}
	httpClient.httpClient.getHostConfiguration().setHost(host, port,
		protocol);
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     * @param contentType
     * @param charset
     * @param methodName
     * @param content
     */
    private static void setContent(HttpMethod method, String contentType,
	    String charset, String methodName, Object content) {
	// content is set, but params has not set
	// use default content type and encoding when posting
	if (methodName.equals("POST")) {
	    if (content instanceof String) {
		try {
		    ((PostMethod) method)
			    .setRequestEntity(new StringRequestEntity(
				    (String) content, contentType, charset));
		} catch (UnsupportedEncodingException e) {
		    throw new RuntimeException("Unsupported Charset");
		}
	    } else {
		NativeObject element;
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		String eName;
		String eValue;
		// create pairs using name-value pairs
		for (int i = 0; i < ((NativeArray) content).getLength(); i++) {
		    if (((NativeArray) content).get(i, (NativeArray) content) instanceof NativeObject) {
			element = (NativeObject) ((NativeArray) content).get(i,
				(NativeArray) content);
			if (ScriptableObject.getProperty(element, "name") instanceof String
				&& ScriptableObject.getProperty(element,
					"value") instanceof String) {
			    eName = (String) ScriptableObject.getProperty(
				    element, "name");
			    eValue = (String) ScriptableObject.getProperty(
				    element, "value");
			    pairs.add(new NameValuePair(eName, eValue));
			} else {
			    throw new RuntimeException(
				    "Invalid content definition, objects of the content array "
					    + "should consists with strings for both key/value");
			}

		    } else {
			throw new RuntimeException(
				"Invalid content definition, content array should contain "
					+ "Javascript Objects");
		    }
		}
		((PostMethod) method).setRequestBody(pairs
			.toArray(new NameValuePair[pairs.size()]));
	    }
	} else if (methodName.equals("GET")) {
	    if (content instanceof String) {
		method.setQueryString((String) content);
	    } else {
		NativeObject element;
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		String eName;
		String eValue;
		// create pairs using name-value pairs
		for (int i = 0; i < ((NativeArray) content).getLength(); i++) {
		    if (((NativeArray) content).get(i, (NativeArray) content) instanceof NativeObject) {
			element = (NativeObject) ((NativeArray) content).get(i,
				(NativeArray) content);
			if (ScriptableObject.getProperty(element, "name") instanceof String
				&& ScriptableObject.getProperty(element,
					"value") instanceof String) {
			    eName = (String) ScriptableObject.getProperty(
				    element, "name");
			    eValue = (String) ScriptableObject.getProperty(
				    element, "value");
			    pairs.add(new NameValuePair(eName, eValue));
			} else {
			    throw new RuntimeException(
				    "Invalid content definition, objects of the content array "
					    + "should consists with strings for both key/value");
			}

		    } else {
			throw new RuntimeException(
				"Invalid content definition, content array should contain "
					+ "Javascript Objects");
		    }
		}
		method.setQueryString(pairs.toArray(new NameValuePair[pairs
			.size()]));
	    }
	} else if (methodName.equals("PUT")) {
	    // here, the method now is PUT
	    if (content != null) {
		if (content instanceof String) {
		    try {
			((PutMethod) method)
				.setRequestEntity(new StringRequestEntity(
					(String) content, contentType, charset));
		    } catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported Charset");
		    }
		} else {
		    throw new RuntimeException(
			    "Invalid content definition, content should be a string when PUT "
				    + "method is used");
		}
	    }
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     * @param contentType
     * @param charset
     * @param methodName
     * @param content
     * @param params
     */
    private static void setParams(PooledHttpClientHostObject httpClient,
	    HttpMethod method, String contentType, String charset,
	    String methodName, Object content, NativeObject params) {
	// other parameters have been set, they are properly set to the
	// corresponding context
	if (ScriptableObject.getProperty(params, "cookiePolicy") instanceof String) {
	    method.getParams().setCookiePolicy(
		    (String) ScriptableObject.getProperty(params,
			    "cookiePolicy"));
	} else if (!ScriptableObject.getProperty(params, "cookiePolicy")
		.equals(UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}

	if (ScriptableObject.getProperty(params, "contentType") instanceof String) {
	    contentType = (String) ScriptableObject.getProperty(params,
		    "contentType");
	} else if (!ScriptableObject.getProperty(params, "contentType").equals(
		UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}

	if (ScriptableObject.getProperty(params, "charset") instanceof String) {
	    charset = (String) ScriptableObject.getProperty(params, "charset");
	} else if (!ScriptableObject.getProperty(params, "charset").equals(
		UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}

	if (ScriptableObject.getProperty(params, "timeout") instanceof Integer) {
	    method.getParams().setSoTimeout(
		    (Integer) ScriptableObject.getProperty(params, "timeout"));
	} else if (!ScriptableObject.getProperty(params, "timeout").equals(
		UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}

	if (ScriptableObject.getProperty(params, "doAuthentication") instanceof Boolean) {
	    method.setDoAuthentication((Boolean) ScriptableObject.getProperty(
		    params, "doAuthentication"));
	} else if (!ScriptableObject.getProperty(params, "doAuthentication")
		.equals(UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}

	if (ScriptableObject.getProperty(params, "followRedirect") instanceof Boolean) {
	    method.setFollowRedirects((Boolean) ScriptableObject.getProperty(
		    params, "followRedirect"));
	} else if (!ScriptableObject.getProperty(params, "followRedirect")
		.equals(UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}

	if (methodName.equals("POST")) {
	    // several parameters are specific to POST method
	    if (ScriptableObject.getProperty(params, "contentChunked") instanceof Boolean) {
		boolean chuncked = (Boolean) ScriptableObject.getProperty(
			params, "contentChunked");
		((PostMethod) method).setContentChunked(chuncked);
		if (chuncked && content != null) {
		    // if contentChucked is set true, then
		    // InputStreamRequestEntity or
		    // MultipartRequestEntity is used
		    if (content instanceof String) {
			// InputStreamRequestEntity for string content
			((PostMethod) method)
				.setRequestEntity(new InputStreamRequestEntity(
					new ByteArrayInputStream(
						((String) content).getBytes())));
		    } else {
			// MultipartRequestEntity for Name-Value pair
			// content
			NativeObject element;
			List<StringPart> parts = new ArrayList<StringPart>();
			String eName;
			String eValue;
			// create pairs using name-value pairs
			for (int i = 0; i < ((NativeArray) content).getLength(); i++) {
			    if (((NativeArray) content).get(i,
				    (NativeArray) content) instanceof NativeObject) {
				element = (NativeObject) ((NativeArray) content)
					.get(i, (NativeArray) content);
				if (ScriptableObject.getProperty(element,
					"name") instanceof String
					&& ScriptableObject.getProperty(
						element, "value") instanceof String) {
				    eName = (String) ScriptableObject
					    .getProperty(element, "name");
				    eValue = (String) ScriptableObject
					    .getProperty(element, "value");
				    parts.add(new StringPart(eName, eValue));
				} else {
				    throw new RuntimeException(
					    "Invalid content definition, objects of the content"
						    + " array should consists with strings for both key/value");
				}

			    } else {
				throw new RuntimeException(
					"Invalid content definition, content array should contain "
						+ "Javascript Objects");
			    }
			}
			((PostMethod) method)
				.setRequestEntity(new MultipartRequestEntity(
					parts.toArray(new Part[parts.size()]),
					method.getParams()));
		    }
		}

	    } else if (ScriptableObject.getProperty(params, "contentChunked")
		    .equals(UniqueTag.NOT_FOUND) && content != null) {
		// contentChunking has not used
		if (content instanceof String) {
		    try {
			((PostMethod) method)
				.setRequestEntity(new StringRequestEntity(
					(String) content, contentType, charset));
		    } catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported Charset");
		    }
		} else {
		    NativeObject element;
		    List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		    String eName;
		    String eValue;
		    // create pairs using name-value pairs
		    for (int i = 0; i < ((NativeArray) content).getLength(); i++) {
			if (((NativeArray) content).get(i,
				(NativeArray) content) instanceof NativeObject) {
			    element = (NativeObject) ((NativeArray) content)
				    .get(i, (NativeArray) content);
			    if (ScriptableObject.getProperty(element, "name") instanceof String
				    && ScriptableObject.getProperty(element,
					    "value") instanceof String) {
				eName = (String) ScriptableObject.getProperty(
					element, "name");
				eValue = (String) ScriptableObject.getProperty(
					element, "value");
				pairs.add(new NameValuePair(eName, eValue));
			    } else {
				throw new RuntimeException(
					"Invalid content definition, objects of the content array "
						+ "should consists with strings for both key/value");
			    }

			} else {
			    throw new RuntimeException(
				    "Invalid content definition, content array should contain "
					    + "Javascript Objects");
			}
		    }
		    ((PostMethod) method).setRequestBody(pairs
			    .toArray(new NameValuePair[pairs.size()]));
		}
	    } else if (!ScriptableObject.getProperty(params, "contentChunked")
		    .equals(UniqueTag.NOT_FOUND)) {
		throw new RuntimeException(
			"Method parameters should be Strings");
	    }

	} else if (methodName.equals("GET")) {
	    // here, the method now is GET
	    if (content != null) {
		if (content instanceof String) {
		    method.setQueryString((String) content);
		} else {
		    NativeObject element;
		    List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		    String eName;
		    String eValue;
		    // create pairs using name-value pairs
		    for (int i = 0; i < ((NativeArray) content).getLength(); i++) {
			if (((NativeArray) content).get(i,
				(NativeArray) content) instanceof NativeObject) {
			    element = (NativeObject) ((NativeArray) content)
				    .get(i, (NativeArray) content);
			    if (ScriptableObject.getProperty(element, "name") instanceof String
				    && ScriptableObject.getProperty(element,
					    "value") instanceof String) {
				eName = (String) ScriptableObject.getProperty(
					element, "name");
				eValue = (String) ScriptableObject.getProperty(
					element, "value");
				pairs.add(new NameValuePair(eName, eValue));
			    } else {
				throw new RuntimeException(
					"Invalid content definition, objects of the content array "
						+ "should consists with strings for both key/value");
			    }

			} else {
			    throw new RuntimeException(
				    "Invalid content definition, content array should contain "
					    + "Javascript Objects");
			}
		    }
		    method.setQueryString(pairs.toArray(new NameValuePair[pairs
			    .size()]));
		}
	    }
	} else if (methodName.equals("PUT")) {
	    // several parameters are specific to PUT method
	    if (ScriptableObject.getProperty(params, "contentChunked") instanceof Boolean) {
		boolean chuncked = (Boolean) ScriptableObject.getProperty(
			params, "contentChunked");
		((PutMethod) method).setContentChunked(chuncked);
		if (chuncked && content != null) {
		    // if contentChucked is set true, then
		    // InputStreamRequestEntity or
		    // MultipartRequestEntity is used
		    if (content instanceof String) {
			// InputStreamRequestEntity for string content
			((PostMethod) method)
				.setRequestEntity(new InputStreamRequestEntity(
					new ByteArrayInputStream(
						((String) content).getBytes())));
		    } else {
			throw new RuntimeException(
				"Invalid content definition, content should be a string when PUT "
					+ "method is used");
		    }
		}

	    } else if (ScriptableObject.getProperty(params, "contentChunked")
		    .equals(UniqueTag.NOT_FOUND) && content != null) {
		// contentChunking has not used
		if (content instanceof String) {
		    try {
			((PostMethod) method)
				.setRequestEntity(new StringRequestEntity(
					(String) content, contentType, charset));
		    } catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported Charset");
		    }
		} else {
		    throw new RuntimeException(
			    "Invalid content definition, content should be a string when PUT "
				    + "method is used");
		}
	    } else if (!ScriptableObject.getProperty(params, "contentChunked")
		    .equals(UniqueTag.NOT_FOUND)) {
		throw new RuntimeException(
			"Method parameters should be Strings");
	    }

	}

	// check whether preemptive authentication is used
	if (ScriptableObject.getProperty(params, "preemptiveAuth") instanceof Boolean) {
	    httpClient.httpClient.getParams().setAuthenticationPreemptive(
		    (Boolean) ScriptableObject.getProperty(params,
			    "preemptiveAuth"));
	} else if (!ScriptableObject.getProperty(params, "preemptiveAuth")
		.equals(UniqueTag.NOT_FOUND)) {
	    throw new RuntimeException("Method parameters should be Strings");
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpMethod
     * @param headers
     */
    private static void setHeaders(HttpMethod httpMethod, NativeArray headers) {
	// headers array is parsed and headers are set
	String hName;
	String hValue;
	NativeObject header;
	for (int i = 0; i < headers.getLength(); i++) {
	    header = (NativeObject) headers.get(i, headers);
	    if (ScriptableObject.getProperty(header, "name") instanceof String
		    && ScriptableObject.getProperty(header, "value") instanceof String) {

		hName = (String) ScriptableObject.getProperty(header, "name");
		hValue = (String) ScriptableObject.getProperty(header, "value");

		httpMethod.setRequestHeader(hName, hValue);
	    } else {
		throw new RuntimeException(
			"Name-Value pairs of headers should be Strings");
	    }
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     */
    private static void setCookies(PooledHttpClientHostObject httpClient) {
	String domain;
	String name;
	String value;
	String path = PooledHttpClientHostObject.PATH;
	Object age = PooledHttpClientHostObject.AGE;
	boolean secure = PooledHttpClientHostObject.SECURE;

	NativeObject cookie;

	// iterate through cookies and insert them into the httpstate
	for (int i = 0; i < httpClient.cookies.getLength(); i++) {
	    cookie = (NativeObject) httpClient.cookies.get(i,
		    httpClient.cookies);
	    if (ScriptableObject.getProperty(cookie, "domain") instanceof String) {
		domain = (String) ScriptableObject
			.getProperty(cookie, "domain");
	    } else {
		throw new RuntimeException(
			"domain property of Cookies should be a String");
	    }

	    if (ScriptableObject.getProperty(cookie, "name") instanceof String) {
		name = (String) ScriptableObject.getProperty(cookie, "name");
	    } else {
		throw new RuntimeException(
			"name property of Cookies should be a String");
	    }

	    if (ScriptableObject.getProperty(cookie, "value") instanceof String) {
		value = (String) ScriptableObject.getProperty(cookie, "value");
	    } else {
		throw new RuntimeException(
			"value property of Cookies should be a String");
	    }

	    if (ScriptableObject.getProperty(cookie, "path") instanceof String) {
		path = (String) ScriptableObject.getProperty(cookie, "path");
	    } else if (!ScriptableObject.getProperty(cookie, "path").equals(
		    UniqueTag.NOT_FOUND)) {
		throw new RuntimeException(
			"path property of Cookies should be a String");
	    }

	    if (ScriptableObject.getProperty(cookie, "age") instanceof Date) {
		age = (Date) ScriptableObject.getProperty(cookie, "age");
	    } else if (ScriptableObject.getProperty(cookie, "age") instanceof Integer) {
		age = (Integer) ScriptableObject.getProperty(cookie, "age");
	    } else if (!ScriptableObject.getProperty(cookie, "age").equals(
		    UniqueTag.NOT_FOUND)) {
		throw new RuntimeException(
			"age property of Cookies should be an Integer or Date");
	    }

	    if (ScriptableObject.getProperty(cookie, "secure") instanceof Boolean) {
		secure = (Boolean) ScriptableObject.getProperty(cookie,
			"secure");
	    } else if (!ScriptableObject.getProperty(cookie, "secure").equals(
		    UniqueTag.NOT_FOUND)) {
		throw new RuntimeException(
			"name property of Cookies should be a String");
	    }

	    if (age != null) {
		if (age instanceof Date) {
		    // cookie expire date has set
		    httpClient.httpClient.getState().addCookie(
			    new Cookie(domain, name, value, path, (Date) age,
				    secure));
		} else {
		    // cookie TTL is set
		    httpClient.httpClient.getState().addCookie(
			    new Cookie(domain, name, value, path,
				    (Integer) age, secure));
		}
	    } else {
		// cookie expiration not set, user default values
		httpClient.httpClient.getState().addCookie(
			new Cookie(domain, name, value, path, (Date) age,
				secure));
	    }
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     * @param authSchemes
     */
    private static void setProxyCredentials(
	    PooledHttpClientHostObject httpClient, List<String> authSchemes) {
	// gets proxy credentials of the scheme
	if (ScriptableObject.getProperty(httpClient.proxyCredentials,
		"credentials") instanceof NativeObject) {

	    NativeObject creds = (NativeObject) ScriptableObject.getProperty(
		    httpClient.proxyCredentials, "credentials");

	    if (ScriptableObject.getProperty(creds, "username") instanceof String) {
		// gets the values about scope of the auth scheme
		NativeObject scope;
		String host = AuthScope.ANY_HOST;
		int port = AuthScope.ANY_PORT;
		String realm = AuthScope.ANY_REALM;
		String scheme = AuthScope.ANY_SCHEME;
		String username;
		String password;

		username = (String) ScriptableObject.getProperty(creds,
			"username");

		if (!username.equals("")) {
		    // proxy credentials username shouldn't be an empty
		    // string
		    if (ScriptableObject.getProperty(creds, "password") instanceof String) {
			password = (String) ScriptableObject.getProperty(creds,
				"password");
		    } else {
			password = "";
		    }

		    // gets the proxy authentication scheme scope
		    if (ScriptableObject.getProperty(
			    httpClient.proxyCredentials, "scope") instanceof NativeObject) {

			scope = (NativeObject) ScriptableObject.getProperty(
				httpClient.proxyCredentials, "scope");

			if (ScriptableObject.getProperty(scope, "host") instanceof String) {
			    host = (String) ScriptableObject.getProperty(scope,
				    "host");
			}
			if (ScriptableObject.getProperty(scope, "port") instanceof Integer) {
			    port = (Integer) ScriptableObject.getProperty(
				    scope, "port");
			}
			if (ScriptableObject.getProperty(scope, "realm") instanceof Integer) {
			    realm = (String) ScriptableObject.getProperty(
				    scope, "realm");
			}
			if (ScriptableObject.getProperty(scope, "scheme") instanceof Integer) {
			    scheme = (String) ScriptableObject.getProperty(
				    scope, "scheme");
			}
		    }

		    if (authSchemes.contains("NTLM")) {
			// NTLM authentication scheme has set, must use
			// NTCredentials
			String hostNT;
			String domainNT;

			// for NTLM scheme both client host and domain
			// should be specified
			if (ScriptableObject.getProperty(creds, "host") instanceof String
				&& ScriptableObject
					.getProperty(creds, "domain") instanceof String) {
			    hostNT = (String) ScriptableObject.getProperty(
				    creds, "host");
			    domainNT = (String) ScriptableObject.getProperty(
				    creds, "domain");

			    httpClient.httpClient
				    .getState()
				    .setProxyCredentials(
					    new AuthScope(host, port, realm,
						    scheme),
					    new NTCredentials(username,
						    password, hostNT, domainNT));
			} else {
			    // NTLM is used, but no proper configurations
			    // have been set
			    throw new RuntimeException(
				    "Both Host and Domain should be specified if you are using "
					    + "NTLM Authentication Scheme for Proxy");
			}
		    } else {
			// NTLM scheme has not specified, so add just
			// UsernamePasswordCredentials
			// instance
			httpClient.httpClient.getState().setProxyCredentials(
				new AuthScope(host, port, realm, scheme),
				new UsernamePasswordCredentials(username,
					password));
		    }
		} else {
		    throw new RuntimeException(
			    "Username can not be an empty String for Proxy");
		}
	    } else {
		throw new RuntimeException(
			"Username should be a String for Proxy");
	    }
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     * @param authSchemes
     */
    private static void setCredentials(PooledHttpClientHostObject httpClient,
	    List<String> authSchemes) {
	// gets the credentials of the scheme
	if (ScriptableObject.getProperty(httpClient.credentials, "credentials") instanceof NativeObject) {

	    NativeObject creds = (NativeObject) ScriptableObject.getProperty(
		    httpClient.credentials, "credentials");

	    if (ScriptableObject.getProperty(creds, "username") instanceof String) {
		// gets the values about scope of the auth scheme
		NativeObject scope;
		String host = AuthScope.ANY_HOST;
		int port = AuthScope.ANY_PORT;
		String realm = AuthScope.ANY_REALM;
		String scheme = AuthScope.ANY_SCHEME;
		String username;
		String password;

		username = (String) ScriptableObject.getProperty(creds,
			"username");

		if (!username.equals("")) {
		    // if credentials are used the username shouldn't be an
		    // empty string
		    if (ScriptableObject.getProperty(creds, "password") instanceof String) {
			password = (String) ScriptableObject.getProperty(creds,
				"password");
		    } else {
			password = "";
		    }

		    if (ScriptableObject.getProperty(httpClient.credentials,
			    "scope") instanceof NativeObject) {
			// gets the authentication scheme scope details. If
			// this is not set,
			// default scopes are used
			scope = (NativeObject) ScriptableObject.getProperty(
				httpClient.credentials, "scope");

			if (ScriptableObject.getProperty(scope, "host") instanceof String) {
			    host = (String) ScriptableObject.getProperty(scope,
				    "host");
			}
			if (ScriptableObject.getProperty(scope, "port") instanceof Integer) {
			    port = (Integer) ScriptableObject.getProperty(
				    scope, "port");
			}
			if (ScriptableObject.getProperty(scope, "realm") instanceof Integer) {
			    realm = (String) ScriptableObject.getProperty(
				    scope, "realm");
			}
			if (ScriptableObject.getProperty(scope, "scheme") instanceof Integer) {
			    scheme = (String) ScriptableObject.getProperty(
				    scope, "scheme");
			}
		    }

		    if (authSchemes.contains("NTLM")) {
			// NTLM authentication scheme has set, must use
			// NTCredentials
			String hostNT;
			String domainNT;

			// for NTLM scheme both client host and domain
			// should be specified
			if (ScriptableObject.getProperty(creds, "host") instanceof String
				&& ScriptableObject
					.getProperty(creds, "domain") instanceof String) {
			    hostNT = (String) ScriptableObject.getProperty(
				    creds, "host");
			    domainNT = (String) ScriptableObject.getProperty(
				    creds, "domain");

			    httpClient.httpClient.getState().setCredentials(
				    new AuthScope(host, port, realm, scheme),
				    new NTCredentials(username, password,
					    hostNT, domainNT));
			} else {
			    // NTLM is used, but no proper configurations
			    // have been set
			    throw new RuntimeException(
				    "Both Host and Domain should be specified if you are using "
					    + "NTLM Authentication Scheme");
			}
		    } else {
			// NTLM scheme has not specified, so add just
			// UsernamePasswordCredentials
			// instance
			httpClient.httpClient.getState().setCredentials(
				new AuthScope(host, port, realm, scheme),
				new UsernamePasswordCredentials(username,
					password));
		    }
		} else {
		    throw new RuntimeException(
			    "Username can not be an empty String");
		}
	    } else {
		throw new RuntimeException("Username should be a String");
	    }
	}
    }

    /**
     * Used by jsFunction_executeMethod().
     * 
     * @param httpClient
     * @param authSchemes
     */
    private static void setAuthSchemes(PooledHttpClientHostObject httpClient,
	    List<String> authSchemes) {
	// authSchemePriority has been set
	for (int i = 0; i < httpClient.authSchemePriority.getLength(); i++) {
	    if (httpClient.authSchemePriority.get(i,
		    httpClient.authSchemePriority) instanceof String) {
		String currentScheme = (String) httpClient.authSchemePriority
			.get(i, httpClient.authSchemePriority);
		if (currentScheme.equals("NTLM")) {
		    authSchemes.add("NTLM");
		} else if (currentScheme.equals("BASIC")) {
		    authSchemes.add("BASIC");
		} else if (currentScheme.equals("DIGEST")) {
		    authSchemes.add("DIGEST");
		} else {
		    throw new RuntimeException(
			    "Unsupported Authentication Scheme");
		}
	    } else {
		throw new RuntimeException(
			"Authentication Schemes should be Strings values");
	    }
	}
	// sets the AuthScheme priority
	httpClient.httpClient.getParams().setParameter(
		AuthPolicy.AUTH_SCHEME_PRIORITY, authSchemes);
    }
}
