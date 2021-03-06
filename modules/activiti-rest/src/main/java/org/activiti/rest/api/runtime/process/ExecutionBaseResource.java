/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.rest.api.runtime.process;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.impl.ExecutionQueryProperty;
import org.activiti.engine.query.QueryProperty;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;
import org.activiti.rest.api.ActivitiUtil;
import org.activiti.rest.api.DataResponse;
import org.activiti.rest.api.RestResponseFactory;
import org.activiti.rest.api.SecuredResource;
import org.activiti.rest.api.engine.variable.QueryVariable;
import org.activiti.rest.api.engine.variable.QueryVariable.QueryVariableOperation;
import org.activiti.rest.api.engine.variable.RestVariable;
import org.activiti.rest.application.ActivitiRestServicesApplication;
import org.restlet.data.Form;

/**
 * @author Frederik Heremans
 */
public class ExecutionBaseResource extends SecuredResource {

  private static Map<String, QueryProperty> allowedSortProperties = new HashMap<String, QueryProperty>();

  static {
    allowedSortProperties.put("processDefinitionId", ExecutionQueryProperty.PROCESS_DEFINITION_ID);
    allowedSortProperties.put("processDefinitionKey", ExecutionQueryProperty.PROCESS_DEFINITION_KEY);
    allowedSortProperties.put("processInstanceId", ExecutionQueryProperty.PROCESS_INSTANCE_ID);
  }

  protected DataResponse getQueryResponse(ExecutionQueryRequest queryRequest, Form urlQuery) {
    ExecutionQuery query = ActivitiUtil.getRuntimeService().createExecutionQuery();

    // Populate query based on request
    if (queryRequest.getId() != null) {
      query.executionId(queryRequest.getId());
    }
    if (queryRequest.getProcessInstanceId() != null) {
      query.processInstanceId(queryRequest.getProcessInstanceId());
    }
    if (queryRequest.getProcessDefinitionKey() != null) {
      query.processDefinitionKey(queryRequest.getProcessDefinitionKey());
    }
    if (queryRequest.getProcessDefinitionId() != null) {
      query.processDefinitionId(queryRequest.getProcessDefinitionId());
    }
    if (queryRequest.getProcessBusinessKey() != null) {
      query.processInstanceBusinessKey(queryRequest.getProcessBusinessKey());
    }
    if (queryRequest.getActivityId() != null) {
      query.activityId(queryRequest.getActivityId());
    }
    if (queryRequest.getParentId() != null) {
      query.parentId(queryRequest.getParentId());
    }
    if (queryRequest.getMessageEventSubscriptionName() != null) {
      query.messageEventSubscriptionName(queryRequest.getMessageEventSubscriptionName());
    }
    if (queryRequest.getSignalEventSubscriptionName() != null) {
      query.signalEventSubscriptionName(queryRequest.getSignalEventSubscriptionName());
    }

    if(queryRequest.getVariables() != null) {
      addVariables(query, queryRequest.getVariables(), false);
    }
    
    if(queryRequest.getProcessInstanceVariables() != null) {
      addVariables(query, queryRequest.getProcessInstanceVariables(), true);
    }
    return new ExecutionPaginateList(this).paginateList(urlQuery, query, "processInstanceId", allowedSortProperties);
  }

  protected void addVariables(ExecutionQuery processInstanceQuery, List<QueryVariable> variables, boolean process) {

    RestResponseFactory responseFacory = getApplication(ActivitiRestServicesApplication.class).getRestResponseFactory();
    
    for (QueryVariable variable : variables) {
      if (variable.getVariableOperation() == null) {
        throw new ActivitiIllegalArgumentException("Variable operation is missing for variable: " + variable.getName());
      }
      if (variable.getValue() == null) {
        throw new ActivitiIllegalArgumentException("Variable value is missing for variable: " + variable.getName());
      }

      boolean nameLess = variable.getName() == null;

      Object actualValue = responseFacory.getVariableValue(variable);

      // A value-only query is only possible using equals-operator
      if (nameLess && variable.getVariableOperation() != QueryVariableOperation.EQUALS) {
        throw new ActivitiIllegalArgumentException("Value-only query (without a variable-name) is only supported when using 'equals' operation.");
      }

      switch (variable.getVariableOperation()) {

      case EQUALS:
        if (nameLess) {
          if(process) {
            processInstanceQuery.processVariableValueEquals(actualValue);
          } else {
            processInstanceQuery.variableValueEquals(actualValue);
          }
        } else {
          if(process) {
            processInstanceQuery.processVariableValueEquals(variable.getName(), actualValue);
          } else {
            processInstanceQuery.variableValueEquals(variable.getName(), actualValue);
          }
        }
        break;

      case EQUALS_IGNORE_CASE:
        if (actualValue instanceof String) {
          if(process) {
            processInstanceQuery.processVariableValueEqualsIgnoreCase(variable.getName(), (String) actualValue);
          } else {
            processInstanceQuery.variableValueEqualsIgnoreCase(variable.getName(), (String) actualValue);
          }
        } else {
          throw new ActivitiIllegalArgumentException("Only string variable values are supported when ignoring casing, but was: "
                  + actualValue.getClass().getName());
        }
        break;

      case NOT_EQUALS:
        if(process) {
          processInstanceQuery.processVariableValueNotEquals(variable.getName(), actualValue);
        } else {
          processInstanceQuery.variableValueNotEquals(variable.getName(), actualValue);
        }
        break;

      case NOT_EQUALS_IGNORE_CASE:
        if (actualValue instanceof String) {
          if(process) {
            processInstanceQuery.processVariableValueNotEqualsIgnoreCase(variable.getName(), (String) actualValue);
          } else {
            processInstanceQuery.variableValueNotEqualsIgnoreCase(variable.getName(), (String) actualValue);
          }
        } else {
          throw new ActivitiIllegalArgumentException("Only string variable values are supported when ignoring casing, but was: "
                  + actualValue.getClass().getName());
        }
        break;
      default:
        throw new ActivitiIllegalArgumentException("Unsupported variable query operation: " + variable.getVariableOperation());
      }
    }
  }

  protected Execution getExecutionFromRequest() {
    String executionId = getAttribute("executionId");
    if (executionId == null) {
      throw new ActivitiIllegalArgumentException("The executionId cannot be null");
    }
    
   Execution execution = ActivitiUtil.getRuntimeService().createExecutionQuery().executionId(executionId).singleResult();
    if (execution == null) {
      throw new ActivitiObjectNotFoundException("Could not find an execution with id '" + executionId + "'.", Execution.class);
    }
    return execution;
  }

  protected Map<String, Object> getVariablesToSet(ExecutionActionRequest actionRequest) {
    Map<String, Object> variablesToSet = new HashMap<String, Object>(); 
    RestResponseFactory factory = getApplication(ActivitiRestServicesApplication.class).getRestResponseFactory();
    for(RestVariable var : actionRequest.getVariables()) {
      if(var.getName() == null) {
        throw new ActivitiIllegalArgumentException("Variable name is required");
      }
      
      Object actualVariableValue = factory.getVariableValue(var);
      
      variablesToSet.put(var.getName(), actualVariableValue);
    }
    return variablesToSet;
  }
}
