import { useState } from 'react'
import { ChevronLeft, ChevronRight, Pencil, Plus, Power, RotateCcw, Search } from 'lucide-react'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import Modal from '../components/ui/Modal'
import { RoleBadge } from '../components/ui/Badge'
import { SelectInput, TextInput } from '../components/ui/Field'
import { AsyncContent, EmptyState, ErrorAlert, PageHeader } from '../components/ui/Feedback'
import { useToast } from '../components/ui/Toast'
import useAsync from '../hooks/useAsync'
import useDebouncedValue from '../hooks/useDebouncedValue'
import { useAuth } from '../auth/AuthContext'
import { employeeApi } from '../api/endpoints'
import { getErrorMessage, getFieldErrors } from '../utils/errors'
import { formatDate, todayIso } from '../utils/format'

const ROLES = ['EMPLOYEE', 'MANAGER', 'ADMIN']
const PAGE_SIZE = 10

/** Admin: search, create, edit, deactivate and reactivate employees. */
export default function EmployeesPage() {
  const { user } = useAuth()
  const toast = useToast()
  const [filters, setFilters] = useState({ search: '', role: '', department: '', active: 'true' })
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState(null)
  const search = useDebouncedValue(filters.search.trim())

  const employees = useAsync(() => employeeApi.search({
    search: search || undefined,
    role: filters.role || undefined,
    department: filters.department || undefined,
    active: filters.active === '' ? undefined : filters.active,
    page,
    size: PAGE_SIZE,
    sort: 'fullName,asc',
  }), [search, filters.role, filters.department, filters.active, page])
  const departments = useAsync(() => employeeApi.departments(), [])

  const changeFilter = (field) => (event) => {
    setFilters((current) => ({ ...current, [field]: event.target.value }))
    setPage(0)
  }

  const toggleActive = async (employee) => {
    const verb = employee.active ? 'Deactivate' : 'Reactivate'
    const warning = employee.active
      ? '\n\nThey will be signed out immediately and their pending leave requests will be cancelled.' : ''
    if (!window.confirm(`${verb} ${employee.fullName}?${warning}`)) return
    try {
      if (employee.active) await employeeApi.deactivate(employee.id)
      else await employeeApi.activate(employee.id)
      toast.success(`${employee.fullName} ${employee.active ? 'deactivated' : 'reactivated'}`)
      employees.reload()
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const result = employees.data
  return (
    <>
      <PageHeader title="Employees" description="Manage people, roles and reporting lines."
                  actions={<Button icon={Plus} onClick={() => setEditing({})}>Add employee</Button>} />

      <Card bodyClassName="p-0">
        <div className="grid gap-3 border-b border-slate-100 p-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <input aria-label="Search" placeholder="Name, email or code…" value={filters.search}
                   onChange={changeFilter('search')}
                   className="block w-full rounded-lg border-0 py-2 pl-9 pr-3 text-sm ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-brand-500" />
          </div>
          <SelectInput id="role-filter" aria-label="Role" value={filters.role} onChange={changeFilter('role')}>
            <option value="">All roles</option>
            {ROLES.map((role) => <option key={role} value={role}>{role.charAt(0) + role.slice(1).toLowerCase()}</option>)}
          </SelectInput>
          <SelectInput id="department-filter" aria-label="Department" value={filters.department}
                       onChange={changeFilter('department')}>
            <option value="">All departments</option>
            {departments.data?.map((dept) => <option key={dept} value={dept}>{dept}</option>)}
          </SelectInput>
          <SelectInput id="active-filter" aria-label="Status" value={filters.active} onChange={changeFilter('active')}>
            <option value="true">Active</option>
            <option value="false">Deactivated</option>
            <option value="">Everyone</option>
          </SelectInput>
        </div>

        <AsyncContent {...employees}>
          {result?.content.length === 0 ? <EmptyState title="No employees match these filters" /> : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100 text-sm">
                <thead>
                  <tr className="text-left text-xs font-medium uppercase tracking-wide text-slate-400">
                    <th className="px-4 py-2">Employee</th>
                    <th className="px-4 py-2">Role</th>
                    <th className="hidden px-4 py-2 md:table-cell">Department</th>
                    <th className="hidden px-4 py-2 lg:table-cell">Manager</th>
                    <th className="hidden px-4 py-2 lg:table-cell">Joined</th>
                    <th className="px-4 py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {result?.content.map((employee) => (
                    <tr key={employee.id} className={employee.active ? '' : 'bg-slate-50 text-slate-400'}>
                      <td className="px-4 py-3">
                        <p className="font-medium text-slate-700">{employee.fullName}
                          {!employee.active && <span className="ml-2 text-xs font-normal text-red-500">deactivated</span>}
                        </p>
                        <p className="text-xs text-slate-400">{employee.employeeCode} · {employee.email}</p>
                      </td>
                      <td className="px-4 py-3"><RoleBadge role={employee.role} /></td>
                      <td className="hidden px-4 py-3 md:table-cell">
                        {employee.department}<p className="text-xs text-slate-400">{employee.designation}</p>
                      </td>
                      <td className="hidden px-4 py-3 lg:table-cell">{employee.managerName ?? '—'}</td>
                      <td className="hidden whitespace-nowrap px-4 py-3 lg:table-cell">{formatDate(employee.joiningDate)}</td>
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="sm" icon={Pencil} aria-label={`Edit ${employee.fullName}`}
                                  onClick={() => setEditing(employee)} />
                          {employee.id !== user.id && (
                            <Button variant="ghost" size="sm" icon={employee.active ? Power : RotateCcw}
                                    aria-label={employee.active ? 'Deactivate' : 'Reactivate'}
                                    title={employee.active ? 'Deactivate' : 'Reactivate'}
                                    onClick={() => toggleActive(employee)} />
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {result && result.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm text-slate-500">
              <span>{result.totalElements} employees · page {result.page + 1} of {result.totalPages}</span>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" icon={ChevronLeft} disabled={page === 0}
                        onClick={() => setPage(page - 1)}>Previous</Button>
                <Button variant="secondary" size="sm" disabled={page + 1 >= result.totalPages}
                        onClick={() => setPage(page + 1)}>Next <ChevronRight className="h-4 w-4" /></Button>
              </div>
            </div>
          )}
        </AsyncContent>
      </Card>

      {editing && (
        <EmployeeFormModal employee={editing.id ? editing : null} departments={departments.data ?? []}
                           onClose={() => setEditing(null)}
                           onSaved={() => {
                             setEditing(null)
                             employees.reload()
                             departments.reload()
                           }} />
      )}
    </>
  )
}

function EmployeeFormModal({ employee, departments, onClose, onSaved }) {
  const toast = useToast()
  const editing = Boolean(employee)
  const [form, setForm] = useState({
    fullName: employee?.fullName ?? '',
    email: employee?.email ?? '',
    password: '',
    role: employee?.role ?? 'EMPLOYEE',
    department: employee?.department ?? '',
    designation: employee?.designation ?? '',
    managerId: employee?.managerId ?? '',
    joiningDate: employee?.joiningDate ?? todayIso(),
  })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const managers = useAsync(() => employeeApi.managerOptions(), [])

  const set = (field) => (event) => setForm({ ...form, [field]: event.target.value })

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setFieldErrors({})
    const body = {
      ...form,
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      department: form.department.trim(),
      designation: form.designation.trim(),
      managerId: form.managerId === '' ? null : Number(form.managerId),
    }
    if (editing) delete body.password
    try {
      if (editing) await employeeApi.update(employee.id, body)
      else await employeeApi.create(body)
      toast.success(editing ? 'Employee updated' : `${body.fullName} added`)
      onSaved()
    } catch (err) {
      setError(getErrorMessage(err))
      setFieldErrors(getFieldErrors(err))
    } finally {
      setSaving(false)
    }
  }

  // Someone cannot report to themselves, so hide them from the manager list.
  const managerOptions = (managers.data ?? []).filter((option) => option.id !== employee?.id)

  return (
    <Modal open size="lg" title={editing ? `Edit ${employee.fullName}` : 'Add employee'} onClose={onClose}
           footer={(
             <>
               <Button variant="secondary" onClick={onClose}>Cancel</Button>
               <Button type="submit" form="employee-form" loading={saving}>{editing ? 'Save changes' : 'Create employee'}</Button>
             </>
           )}>
      <form id="employee-form" onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert message={error} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextInput id="fullName" label="Full name" value={form.fullName} onChange={set('fullName')}
                     error={fieldErrors.fullName} />
          <TextInput id="email" label="Email" type="email" value={form.email} onChange={set('email')}
                     error={fieldErrors.email} />
          {!editing && (
            <TextInput id="password" label="Initial password" type="password" value={form.password}
                       onChange={set('password')} error={fieldErrors.password}
                       hint="At least 8 characters, with a letter and a number." />
          )}
          <SelectInput id="role" label="Role" value={form.role} onChange={set('role')} error={fieldErrors.role}>
            {ROLES.map((role) => <option key={role} value={role}>{role.charAt(0) + role.slice(1).toLowerCase()}</option>)}
          </SelectInput>
          <TextInput id="department" label="Department" list="department-options" value={form.department}
                     onChange={set('department')} error={fieldErrors.department} />
          <datalist id="department-options">
            {departments.map((dept) => <option key={dept} value={dept} />)}
          </datalist>
          <TextInput id="designation" label="Designation" value={form.designation} onChange={set('designation')}
                     error={fieldErrors.designation} />
          <SelectInput id="managerId" label="Manager" value={form.managerId} onChange={set('managerId')}
                       error={fieldErrors.managerId}
                       hint={form.role === 'ADMIN' ? 'Optional for admins' : 'Approves this person’s leave'}>
            <option value="">— No manager —</option>
            {managerOptions.map((option) => (
              <option key={option.id} value={option.id}>{option.fullName} ({option.department})</option>
            ))}
          </SelectInput>
          <TextInput id="joiningDate" label="Joining date" type="date" value={form.joiningDate}
                     onChange={set('joiningDate')} error={fieldErrors.joiningDate} />
        </div>
      </form>
    </Modal>
  )
}
