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

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

/**
 * @author Frederick Haebin Na
 */
public class PrimitiveWrapFactory extends WrapFactory {
    @Override
    public Object wrap(Context cx, Scriptable scope, Object obj,
	    @SuppressWarnings("rawtypes") Class staticType) {
	if (obj instanceof String || obj instanceof Number
		|| obj instanceof Boolean) {
	    return obj;
	} else if (obj instanceof Character) {
	    char[] a = { ((Character) obj).charValue() };
	    return new String(a);
	}
	return super.wrap(cx, scope, obj, staticType);
    }
}