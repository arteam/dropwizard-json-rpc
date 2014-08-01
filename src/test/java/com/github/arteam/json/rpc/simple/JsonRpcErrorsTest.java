package com.github.arteam.json.rpc.simple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.arteam.json.rpc.simple.server.JsonRpcServer;
import com.github.arteam.json.rpc.simple.service.TeamService;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Date: 7/29/14
 * Time: 7:58 PM
 * Tests various JSON-RPC errors
 *
 * @author Artem Prigoda
 */
public class JsonRpcErrorsTest {

    private JsonRpcServer rpcController = new JsonRpcServer();
    private TeamService teamService = new TeamService();

    private static ObjectMapper objectMapper = new ObjectMapper();

    private static String requestFile(String name) {
        try {
            return Resources.toString(JsonRpcErrorsTest.class.getResource("/error/request/" + name), Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String responseFile(String name) {
        try {
            return Resources.toString(JsonRpcErrorsTest.class.getResource("/error/response/" + name), Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    private static JsonNode json(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testBadJson() {
        String response = rpcController.handle(requestFile("bad_json.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("parse_error.json")));
    }

    @Test
    public void testNotJsonRpc() {
        String response = rpcController.handle(requestFile("not_json_rpc.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request.json")));
    }

    @Test
    public void testNoVersion() {
        String response = rpcController.handle(requestFile("no_version.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request_with_id.json")));
    }

    @Test
    public void testNoMethod() {
        String response = rpcController.handle(requestFile("no_method.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request_with_id.json")));
    }

    @Test
    public void testBadMethodType() {
        String response = rpcController.handle(requestFile("bad_method_type.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request.json")));
    }

    @Test
    public void testBadParamsType() {
        String response = rpcController.handle(requestFile("bad_params_type.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request_with_id.json")));
    }

    @Test
    public void testBadVersion() {
        String response = rpcController.handle(requestFile("bad_version.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request.json")));
    }

    @Test
    public void testBadId() {
        String response = rpcController.handle(requestFile("bad_id.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request.json")));
    }

    @Test
    public void testNotJsonRpc20() {
        String response = rpcController.handle(requestFile("not_json_rpc_20.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_request_with_id.json")));
    }

    @Test
    public void testUnableFindMethod() {
        String response = rpcController.handle(requestFile("not_existed_method.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("method_not_found.json")));
    }

    @Test
    public void testNotAnnotatedMethod() {
        String response = rpcController.handle(requestFile("not_annotated_method.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("method_not_found.json")));
    }

    @Test
    public void testNotPublicMethod() {
        String response = rpcController.handle(requestFile("not_public_method.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("method_not_found.json")));
    }

    @Test
    public void testMethodIsStatic() {
        String response = rpcController.handle(requestFile("static_method.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("method_not_found.json")));
    }

    @Test
    public void testWrongAmountOfArgumentsInMap() {
        String response = rpcController.handle(requestFile("wrong_amount_of_arguments_map.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_params.json")));
    }

    @Test
    public void testWrongAmountOfArgumentsInArray() {
        String response = rpcController.handle(requestFile("wrong_amount_of_arguments_array.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_params.json")));
    }

    @Test
    public void testParamAnnotationIsNotSpecified() {
        String response = rpcController.handle(requestFile("param_annotation_is_not_specified.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_params.json")));
    }

    @Test
    public void testMandatoryParameterIsNotSet() {
        String response = rpcController.handle(requestFile("mandatory_parameter_is_not_set.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_params.json")));
    }

    @Test
    public void testWrongParameterType() {
        String response = rpcController.handle(requestFile("wrong_parameter_type.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_params.json")));
    }

    @Test
    public void testWrongParameterName() {
        String response = rpcController.handle(requestFile("wrong_parameter_name.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("invalid_params.json")));
    }

    @Test
    public void testInternalError() {
        String response = rpcController.handle(requestFile("not_implemented_method.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("internal_error.json")));
    }

    @Test
    public void testInternalErrorNotification() {
        String response = rpcController.handle(requestFile("not_implemented_method_notification.json"), teamService);
        assertThat(response.isEmpty());
    }

    @Test
    public void testBatchErrorResponse() {
        String response = rpcController.handle(requestFile("batch_error.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("batch_response.json")));
    }

    @Test
    public void testUserAuthErrorResponse() {
        String response = rpcController.handle(requestFile("user_exception.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("user_auth_error.json")));
    }

    @Test
    public void testUserAuthBadErrorCode() {
        String response = rpcController.handle(requestFile("bad_code_user_exception.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("internal_error.json")));
    }

    @Test
    public void testUserAuthEmptyMessage() {
        String response = rpcController.handle(requestFile("empty_message_user_exception.json"), teamService);
        assertThat(json(response)).isEqualTo(json(responseFile("internal_error.json")));
    }

}
