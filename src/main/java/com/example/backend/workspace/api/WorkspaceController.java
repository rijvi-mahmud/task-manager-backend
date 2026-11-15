package com.example.backend.workspace.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.backend.common.api.ApiVersions;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.workspace.command.CreateWorkspaceCommand;
import com.example.backend.workspace.command.DeleteWorkspaceCommand;
import com.example.backend.workspace.command.RenameWorkspaceCommand;
import com.example.backend.workspace.command.WorkspaceCommandService;
import com.example.backend.workspace.query.WorkspaceQueryService;
import com.example.backend.workspace.query.WorkspaceView;

/** Workspace endpoints. The OpenAPI contract is documented on {@link WorkspaceApi}. */
@RestController
@RequestMapping(path = ApiVersions.BASE + "/workspaces", version = ApiVersions.V1)
public class WorkspaceController implements WorkspaceApi {

    private final WorkspaceCommandService commands;
    private final WorkspaceQueryService queries;

    public WorkspaceController(WorkspaceCommandService commands, WorkspaceQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @Override
    @GetMapping
    public List<WorkspaceView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return queries.findMine(user.id());
    }

    @Override
    @PostMapping
    public ResponseEntity<WorkspaceView> create(@RequestBody WorkspaceRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        Long id = commands.handle(new CreateWorkspaceCommand(user.id(), body.name()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(queries.findById(id, user.id()));
    }

    @Override
    @GetMapping("/{workspaceId}")
    public WorkspaceView get(@PathVariable Long workspaceId, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.findById(workspaceId, user.id());
    }

    @Override
    @PutMapping("/{workspaceId}")
    public WorkspaceView rename(@PathVariable Long workspaceId, @RequestBody WorkspaceRequest body,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new RenameWorkspaceCommand(workspaceId, user.id(), body.name()));
        return queries.findById(workspaceId, user.id());
    }

    @Override
    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new DeleteWorkspaceCommand(workspaceId, user.id()));
    }
}
