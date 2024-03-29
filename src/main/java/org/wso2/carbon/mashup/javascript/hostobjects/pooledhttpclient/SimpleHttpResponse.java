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

/**
 * @author Frederick Haebin Na
 */
public class SimpleHttpResponse {
    public int statusCode;
    public Object statusText;
    public Object headers;
    public Object response;

    public SimpleHttpResponse(int statusCode, String statusText,
	    String headers, String response) {
	this.statusText = statusText;
	this.headers = headers;
	this.response = response;
	this.statusCode = statusCode;
    }
}