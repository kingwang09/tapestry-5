// Copyright 2007 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry.internal.services;

import org.apache.tapestry.ComponentEventHandler;
import org.apache.tapestry.TapestryConstants;
import org.apache.tapestry.internal.structure.ComponentPageElement;
import org.apache.tapestry.internal.structure.Page;
import org.apache.tapestry.internal.util.Holder;
import org.apache.tapestry.runtime.Component;
import org.apache.tapestry.runtime.RenderCommand;
import org.apache.tapestry.services.ComponentActionRequestHandler;
import org.apache.tapestry.services.MarkupWriterFactory;
import org.apache.tapestry.services.Response;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Similar to {@link ComponentActionRequestHandlerImpl}, but built around the Ajax request cycle, where the action request
 * sends back an immediate JSON response containing the new content.
 */
public class AjaxComponentActionRequestHandler implements ComponentActionRequestHandler
{
    private final RequestPageCache _cache;

    private final MarkupWriterFactory _factory;

    private final PageMarkupRenderer _renderer;

    private final Response _response;

    public AjaxComponentActionRequestHandler(RequestPageCache cache, MarkupWriterFactory factory,
                                             PageMarkupRenderer renderer, Response response)
    {
        _cache = cache;
        _factory = factory;
        _renderer = renderer;
        _response = response;
    }

    public boolean handle(String logicalPageName, String nestedComponentId, String eventType, String[] context,
                          String[] activationContext) throws IOException
    {
        final Page page = _cache.get(logicalPageName);

        ComponentPageElement element = page.getComponentElementByNestedId(nestedComponentId);

        final Holder<Boolean> holder = Holder.create();
        final Holder<IOException> exceptionHolder = Holder.create();

        ComponentEventHandler handler = new ComponentEventHandler()
        {
            @SuppressWarnings("unchecked")
            public boolean handleResult(Object result, Component component, String methodDescription)
            {
                // TODO: Very limiting; event handler should be able to return a component or a StreamResponse
                // as well.  Perhaps others.  The problem is the page. Maybe we need to store the
                // page in a global?

                if (!(result instanceof RenderCommand)) throw new IllegalArgumentException(
                        String.format("Return type %s is not supported.", result.getClass().getName()));


                try
                {
                    new AjaxResponseGenerator(page, (RenderCommand) result, _factory, _renderer).sendClientResponse(
                            _response);
                }
                catch (IOException ex)
                {
                    exceptionHolder.put(ex);
                }


                holder.put(true);

                return true;
            }
        };

        page.getRootElement().triggerEvent(TapestryConstants.ACTIVATE_EVENT, activationContext, handler);

        if (exceptionHolder.hasValue()) throw exceptionHolder.get();

        if (holder.hasValue()) return holder.get();

        element.triggerEvent(eventType, context, handler);

        if (holder.hasValue()) return true;

        PrintWriter pw = _response.getPrintWriter("text/javascript");

        pw.print("{ }");

        pw.flush();

        return true;
    }
}
