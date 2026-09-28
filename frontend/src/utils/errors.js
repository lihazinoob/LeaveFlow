/** Reads the backend's ApiError JSON ({ message, fieldErrors }) from an Axios error. */

export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error?.response) {
    return 'Cannot reach the server. Please check your connection.'
  }
  return error.response.data?.message ?? fallback
}

export function getFieldErrors(error) {
  return error?.response?.data?.fieldErrors ?? {}
}
