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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mozilla.javascript.ClassCache;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * @author Frederick Haebin Na
 */
public class PooledHttpClientHostObjectTest {
    private Server server;
    private int port = 11273;

    @Before
    public void setUp() throws Exception {
	server = new Server(port);
	Handler handler = new AbstractHandler() {
	    public void handle(String target, HttpServletRequest request,
		    HttpServletResponse response, int dispatch) {
		try {
		    response.setContentType("text/html");
		    response.setStatus(HttpServletResponse.SC_OK);

		    String uri = request.getRequestURI();
		    if (uri.equals("") || uri.equals("/")) {
			response.getOutputStream().print("<b>Hello World!</b>");
		    } else {
			// sleep for 1 sec.
			// for mult url calls
			Thread.sleep(1000);
			response.getOutputStream().print(uri);
		    }
		    ((Request) request).setHandled(true);
		} catch (Exception e) {
		    throw new RuntimeException(e);
		}
	    }
	};
	server.setHandler(handler);
	server.start();
    }

    @Test
    public void testJsFunction_executeMethodWithSingleUrl() throws Exception {
	Context ctx = ContextFactory.getGlobal().enterContext();
	Scriptable scope = ctx.initStandardObjects();
	Scriptable scriptable = PooledHttpClientHostObject.jsConstructor(ctx,
		new Object[0], null, false);
	new ClassCache().associate((ScriptableObject) scriptable);

	Object[] args = new Object[2];
	args[0] = "GET";
	args[1] = "http://localhost:" + port;
	Scriptable result = PooledHttpClientHostObject
		.jsFunction_executeMethod(ctx, scriptable, args, null);

	assertThat(result.get("statusCode", result).toString(), is("200"));
	assertThat(result.get("statusText", result).toString(), is("OK"));
	assertThat(result.get("response", result).toString(),
		is("<b>Hello World!</b>"));
	System.out.println(result.get("headers", result).toString());
	Context.exit();
    }

    @Test
    public void testJsFunction_executeMethodWithUrls() throws Exception {
	Context ctx = ContextFactory.getGlobal().enterContext();
	Scriptable scope = ctx.initStandardObjects();
	Scriptable scriptable = PooledHttpClientHostObject.jsConstructor(ctx,
		new Object[0], null, false);
	new ClassCache().associate((ScriptableObject) scriptable);

	Object[] urls = new Object[30];
	for (int i = 0; i < urls.length; i++) {
	    urls[i] = "http://localhost:" + port + "/page" + i;
	}

	Object[] args = new Object[2];
	args[0] = "GET";
	args[1] = ctx.newArray(scope, urls);

	long time = System.currentTimeMillis();
	Scriptable result = PooledHttpClientHostObject
		.jsFunction_executeMethod(ctx, scriptable, args, null);
	time = System.currentTimeMillis() - time;
	int minSec = (int) Math.ceil((float) urls.length
		/ PooledHttpClientHostObject.CONN_POOL_THREAD_MAX);
	assertThat(urls.length + " url calls with 1 sec delay.", time,
		lessThan(minSec * 1000 + 500L));

	Scriptable tmp;
	for (int i = 0; i < urls.length; i++) {
	    tmp = (Scriptable) result.get(i, result);
	    assertThat(tmp.get("statusCode", tmp).toString(), is("200"));
	    assertThat(tmp.get("statusText", tmp).toString(), is("OK"));
	    assertThat(tmp.get("response", tmp).toString(), is("/page" + i));
	}
	Context.exit();
    }

    @Test
    public void testJsFunction_executeMethodWithUrlsMax() throws Exception {
	// #TODO test for max con per thread, max con for global
	// what happens if surpasses the limit?
	// need to do the profiling.
    }

    @After
    public void tearDown() throws Exception {
	server.stop();
    }
}
