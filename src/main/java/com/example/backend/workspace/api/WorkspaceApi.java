package com.example.backend.workspace.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.example.backend.common.api.ApiTags;
import com.example.backend.common.api.ProblemResponses;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.workspace.query.WorkspaceView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** OpenAPI contract for workspaces, implemented by {@link WorkspaceController}. */
@Tag(name = ApiTags.WORKSPACES)
public interface WorkspaceApi {

    @Operation(operationId = "listWorkspaces", summary = "Workspaces I belong to", description = "Sorted by name.")
    @ApiResponse(responseCode = "200", description = "The current user's workspaces")
    List<WorkspaceView> list(@Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "createWorkspace", summary = "Create a workspace", description = "The creator becomes its owner.")
    @ApiResponse(responseCode = "201", description = "Workspace created",
            headers = @Header(name = "Location", description = "URI of the new workspace",
                    schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = WorkspaceView.class)))
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    ResponseEntity<WorkspaceView> create(@Valid WorkspaceRequest body, @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "getWorkspace", summary = "Get a workspace")
    @ApiResponse(responseCode = "200", description = "The workspace")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    WorkspaceView get(@Parameter(description = "Workspace id") Long workspaceId,
                      @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "renameWorkspace", summary = "Rename a workspace", description = "Owners only.")
    @ApiResponse(responseCode = "200", description = "The renamed workspace")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    WorkspaceView rename(@Parameter(description = "Workspace id") Long workspaceId, @Valid WorkspaceRequest body,
                         @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "deleteWorkspace", summary = "Delete a workspace",
            description = "Owners only. Deletes all of its tasks.")
    @ApiResponse(responseCode = "204", description = "Workspace deleted")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    void delete(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(hidden = true) AuthenticatedUser user);
}
