class WorkboardError(Exception):
    code = "application_error"


class NotFound(WorkboardError):
    code = "not_found"


class Conflict(WorkboardError):
    code = "conflict"


class InvalidTransition(WorkboardError):
    code = "invalid_transition"


class AuthenticationFailed(WorkboardError):
    code = "invalid_credentials"
