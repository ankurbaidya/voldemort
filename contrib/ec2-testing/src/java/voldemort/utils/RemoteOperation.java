/*
 * Copyright 2009 LinkedIn, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.utils;

import java.util.List;

/**
 * A RemoteOperation represents an operation that can be performed on a remote
 * system. For example, it may represent the ability to remotely start/stop
 * server and test nodes, deploy files, and so forth.
 * 
 * <p/>
 * 
 * Should the operation span multiple remote hosts, implementations should
 * perform the operation in parallel with respect to the remote hosts.
 * 
 * @author Kirk True
 * 
 * @param <T> Data that is returned from an operation, specific to that
 *        operation
 */

public interface RemoteOperation<T> {

    /**
     * Executes the specific remote operation. A remote operation can span
     * multiple remote hosts, so the results for each remote host are captured
     * in a list.
     * 
     * @return List of results
     * 
     * @throws RemoteOperationException Thrown if an error occurred performing
     *         the remote operation
     */

    public List<T> execute() throws RemoteOperationException;

}