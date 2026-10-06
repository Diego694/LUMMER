/**
 * Tipos compartidos para el frontend web de Registro Académico.
 * Derivados del esquema de Supabase (schema.sql, migraciones 001-011) y el modelo de datos cliente.
 */

// Roles de usuario del sistema (perfiles.rol)
export type Rol =
  | 'admin'
  | 'administrador'
  | 'docente'
  | 'coordinador'
  | 'auxiliar'
  | 'Administrador'
  | 'Docente'
  | 'Coordinador'
  | 'Auxiliar'
  | 'Administrativo';

// Institución / Colegio
export interface Colegio {
  id: string;
  nombre: string;
  codigo_registro?: string | null;
  aviso_token?: string;
  activo?: boolean;
  creado_en?: string;
  created_at?: string;
}

// Perfil de personal (tabla perfiles + sesión extendida)
export interface Perfil {
  id: string;
  colegio_id: string;
  rol: Rol | string;
  carrera?: string | null;
  nombre?: string | null;
  foto_path?: string | null;
  email?: string | null;
  colegio?: string;
  superadmin?: boolean;
}

// Nivel / Carrera
export interface Nivel {
  id?: string;
  colegio_id?: string;
  nombre: string;
}

// Grado / Ciclo o sección
export interface Grado {
  id?: string;
  colegio_id?: string;
  nivel: string;
  nombre: string;
}

// Estado del alumno
export type EstadoAlumno = 'ACTIVO' | 'INACTIVO' | 'EGRESADO' | string;

// Estudiante / Alumno
export interface Alumno {
  id: string;
  colegio_id?: string;
  codigo: string;
  nombre: string;
  nivel: string;
  grado: string;
  apoderado?: string;
  estado: EstadoAlumno;
  user_id?: string | null;
  nombres?: string | null;
  apellidos?: string | null;
  dni?: string | null;
  foto_path?: string | null;
  foto_data?: string | null;
  aprobado?: boolean;
  consentimiento_en?: string | null;
  registrado_en?: string;
  apoderado_telefono?: string | null;
  apoderado_email?: string | null;
  qr_secreto?: string;
  codigo_apoderado?: string | null;
  notif_token?: string | null;
  aviso?: string[];
}

// Personal docente / administrativo
export interface Docente {
  id: string;
  colegio_id?: string;
  nombre: string;
  profesion?: string;
  rol?: 'Docente' | 'Coordinador' | 'Auxiliar' | 'Administrativo' | string;
  estado?: 'ACTIVO' | 'INACTIVO' | string;
}

// Comunicado institucional
export interface Comunicado {
  id?: string;
  colegio_id?: string;
  titulo: string;
  mensaje: string;
  fecha: string;
  creado_en?: string;
  publicado_por?: string | null;
}

// Origen del registro de asistencia
export type OrigenAsistencia = 'qr' | 'nfc' | 'manual' | 'alumno' | 'masivo' | 'local' | string;

// Registro de asistencia
export interface Asistencia {
  id?: string;
  colegio_id?: string;
  alumno_id: string;
  fecha: string;
  hora: string;
  registrado_por?: string | null;
  registrado_en?: string;
  origen?: OrigenAsistencia | null;
  hora_salida?: string | null;
}

// Tipo de justificación de inasistencia
export type TipoJustificacion = 'Falta justificada' | 'Permiso' | 'Tardanza justificada' | string;

// Justificación de inasistencia / tardanza
export interface Justificacion {
  id?: string;
  colegio_id?: string;
  alumno_id: string;
  fecha: string;
  tipo: TipoJustificacion;
  motivo?: string;
  registrado_por?: string | null;
  creado_en?: string;
}

// Curso de carrera
export interface Curso {
  id?: string;
  colegio_id?: string;
  nivel: string;
  grado?: string | null;
  nombre: string;
  docente?: string;
  activo?: boolean;
}

// Asistencia por curso
export interface AsistenciaCurso {
  id?: string;
  colegio_id?: string;
  alumno_id: string;
  curso_id: string;
  fecha: string;
  hora: string;
  registrado_por?: string | null;
  registrado_en?: string;
  origen?: string | null;
}

// Periodo académico
export interface Periodo {
  id?: string;
  colegio_id?: string;
  nombre: string;
  inicio: string;
  fin?: string | null;
  activo?: boolean;
  cerrado_en?: string | null;
  resumen?: Record<string, unknown> | null;
}

// Tipo de evento en calendario
export type TipoDiaCalendario = 'Feriado' | 'Sin clases' | 'Evento' | string;

// Día de calendario (feriado / sin clases / evento)
export interface DiaCalendario {
  id?: string;
  colegio_id?: string;
  fecha: string;
  tipo: TipoDiaCalendario;
  nombre: string;
}

// Horario (por carrera o general)
export interface Horario {
  id?: string;
  colegio_id?: string;
  nivel?: string | null;
  hora_ingreso: string;
  tolerancia_min?: number;
  hora_salida?: string | null;
  ingreso_desde?: string | null;
  ingreso_hasta?: string | null;
  permanencia_min?: number;
}

// Aviso a apoderado
export interface AvisoApoderado {
  id?: string;
  colegio_id?: string;
  alumno_id: string;
  fecha: string;
  tipo: 'Falta' | 'Tardanza' | 'Llegada' | 'Otro' | string;
  canal?: 'WhatsApp' | string;
  enviado_por?: string | null;
  creado_en?: string;
}

// Registro de auditoría
export interface Auditoria {
  id?: number | string;
  colegio_id?: string | null;
  user_id?: string | null;
  usuario?: string | null;
  accion: 'INSERT' | 'UPDATE' | 'DELETE' | string;
  tabla: string;
  registro_id?: string | null;
  detalle?: Record<string, unknown> | null;
  creado_en?: string;
}

// Elemento en la cola de sincronización sin conexión
export interface ItemCola<T = any> {
  id: string;
  en: number;
  tipo: 'asistencia' | 'salida' | string;
  row: T;
  clave?: string;
  intentos: number;
  rechazado?: boolean;
  ultimoError?: string;
}

// --- Tipos de módulos de lógica pura ---

// calendario.js
export interface HorarioEfectivo {
  definido: boolean;
  ingreso: string | null;
  tolerancia: number;
  limite: string;
  salida: string | null;
  desde: string | null;
  hasta: string | null;
  permanencia: number;
}

export interface TablaLimites {
  general: string;
  porNivel: Record<string, string>;
}

export type EstadoIngreso = 'temprano' | 'puntual' | 'tarde' | 'cerrado';

export interface SalidaPermitidaResult {
  ok: boolean;
  desde: string;
}

// stats.js
export interface ResumenDia {
  activos: number;
  presentes: number;
  tardes: number;
  puntuales: number;
  ausentes: number;
  pct: number;
}

export interface SerieDiariaPunto {
  fecha: string;
  presentes: number;
  tardes: number;
  pct: number;
}

export interface PorGradoItem {
  key: string;
  nivel: string;
  grado: string;
  total: number;
  presentes: number;
  pct: number;
}

export interface PorNivelItem {
  nivel: string;
  total: number;
}

export interface BajaAsistenciaItem {
  alumno: Alumno;
  presentes: number;
  dias: number;
  pct: number;
}

export interface ResumenAlumno {
  presentes: number;
  tardes: number;
  ausentes: number;
  pct: number;
}

export interface MatrizFila {
  alumno: Alumno;
  celdas: Record<string, 'P' | 'T' | 'J' | 'F' | string>;
  p: number;
  t: number;
  j: number;
  f: number;
  pct: number;
  pctJust: number;
}

export interface MatrizAsistenciaResult {
  dias: string[];
  filas: MatrizFila[];
  resumen: {
    alumnos: number;
    dias: number;
    pct: number;
  };
}

export type AccionQuiosco = 'entrada' | 'salida' | 'ya_ingreso' | 'dup_salida';

export interface NormalizarFilaValida {
  nombre: string;
  codigo: string;
  nivel: string;
  grado: string;
  apoderado: string;
  estado: EstadoAlumno;
  aviso: string[];
}

export interface NormalizarFilaError {
  linea: number;
  motivo: string;
}

export interface NormalizarImportResult {
  validas: NormalizarFilaValida[];
  errores: NormalizarFilaError[];
}

// riesgo.js
export type NivelRiesgo = 'critico' | 'alerta' | 'ok';

export interface RiesgoAlumno {
  alumno: Alumno;
  dias: number;
  faltas: number;
  justificadas: number;
  pctFaltas: number;
  nivel: NivelRiesgo;
}

export interface OpcionesCalcularRiesgo {
  alumnos: Alumno[];
  asistencias: Asistencia[];
  justificaciones?: Justificacion[];
  noLectivos?: Map<string, DiaCalendario>;
  desde: string;
  hasta: string;
  limite?: number;
  aviso?: number;
  hoy?: string;
}

// promocion.js
export interface MovimientoPromocion {
  alumno: Alumno;
  de: string;
  a: string;
  carrera: string;
  ciclo: string;
}

export interface GradoNuevoPromocion {
  nivel: string;
  nombre: string;
}

export interface PlanPromocion {
  mover: MovimientoPromocion[];
  egresan: Alumno[];
  sinCiclo: Alumno[];
  gradosNuevos: GradoNuevoPromocion[];
}

export interface CambioPromocion {
  id: string;
  cambios: {
    grado?: string;
    estado?: string;
  };
}

// fusion.js
export interface DatosFusionLocal {
  niveles?: Nivel[];
  grados?: Grado[];
  alumnos?: Alumno[];
  asistencias?: Asistencia[];
  comunicados?: Comunicado[];
  cursos?: Curso[];
  justificaciones?: Justificacion[];
}

export interface DatosFusionRemoto {
  niveles?: (Nivel | string)[];
  grados?: Grado[];
  alumnos?: Alumno[];
  comunicados?: Comunicado[];
  cursos?: Curso[];
}

export interface PlanFusionResumen {
  niveles: number;
  grados: number;
  alumnos: number;
  alumnosExistentes: number;
  asistencias: number;
  comunicados: number;
  cursos: number;
}

export interface PlanFusion {
  niveles: Nivel[];
  grados: Grado[];
  alumnos: Alumno[];
  alumnosExistentes: number;
  comunicados: Comunicado[];
  cursos: Curso[];
  asistencias: Asistencia[];
  resumen?: PlanFusionResumen;
}

export interface AsistenciasOnlineResult {
  filas: Asistencia[];
  sinAlumno: number;
}

// --- Tipos de módulos con @ts-check (Hito TS-2) ---

export interface ItemColaInput<T = any> {
  id?: string;
  en?: number;
  tipo: 'asistencia' | 'salida' | string;
  row: T;
  clave?: string;
  intentos?: number;
  rechazado?: boolean;
  ultimoError?: string;
}

export interface PerfilGuardado {
  user: { id: string; email?: string | null };
  perfil: Perfil;
  en: number;
}

export interface SnapshotDatos {
  alumnos: Alumno[];
  niveles: Nivel[] | string[];
  grados: Grado[];
  comunicados: Comunicado[];
  docentes: Docente[];
  cursos?: Curso[];
  ajustes?: {
    periodos?: Periodo[];
    calendario?: DiaCalendario[];
    horarios?: Horario[];
    [key: string]: unknown;
  };
  [key: string]: unknown;
}

export interface AsistenciaHoy extends Asistencia {
  _pendiente?: boolean;
  _salidaPendiente?: boolean;
}

export interface DBState {
  cid: string | null;
  rol: Rol | string | null;
  perfil: Perfil | null;
  userId: string | null;
  alumnos: Alumno[];
  niveles: string[];
  nivelesRaw: Nivel[];
  grados: Grado[];
  comunicados: Comunicado[];
  docentes: Docente[];
  cursos: Curso[];
  periodos: Periodo[];
  calendario: DiaCalendario[];
  horarios: Horario[];
  noLectivos: Map<string, DiaCalendario>;
  hoy: AsistenciaHoy[];
  hoyFecha: string | null;
  sinConexion: boolean;
}

export interface ErrorLogItem {
  app: string;
  mensaje: string;
  detalle: string;
  url: string;
  agente: string;
}

export interface ApiErrLog {
  registrarError?: (row: ErrorLogItem) => Promise<any> | any;
  [key: string]: any;
}

export interface AndroidBridge {
  configurarAvisos?(url: string, key: string, token: string): void;
  detenerAvisos?(): void;
  configurarAprobacion?(url: string, key: string, token: string): void;
  [key: string]: any;
}

export interface EstadoSync {
  pendientes: number;
  rechazados: number;
  online: boolean;
  enCurso: boolean;
  error: string | null;
}

export interface ResultadoGuardarAsistencias {
  n: number;
  offline: boolean;
}

export interface RespaldoAsistencia {
  codigo: string;
  fecha: string;
  hora: string;
  hora_salida?: string | null;
  origen?: string;
}

export interface RespaldoCurso {
  nombre: string;
  nivel: string;
  grado?: string | null;
}

export interface RespaldoAsistenciaCurso {
  codigo: string;
  curso: RespaldoCurso;
  fecha: string;
  hora: string;
  origen?: string;
}

export interface RespaldoJustificacion {
  codigo: string;
  fecha: string;
  tipo: string;
  motivo?: string;
}

export interface RespaldoAlumno {
  codigo: string;
  nombre: string;
  nivel: string;
  grado: string;
}

export interface RespaldoDatos {
  alumnos: RespaldoAlumno[];
  asistencias: RespaldoAsistencia[];
  asistencias_curso: RespaldoAsistenciaCurso[];
  justificaciones: RespaldoJustificacion[];
}

export interface RespaldoPaquete {
  formato: string;
  version: number;
  creado_en: string;
  instituto?: { id?: string; nombre?: string; [key: string]: any };
  origen?: { modo?: string; app?: string; version?: string; [key: string]: any };
  rango?: { desde: string; hasta: string };
  conteo?: {
    alumnos: number;
    asistencias: number;
    asistencias_curso: number;
    justificaciones: number;
    dias: number;
  };
  datos: RespaldoDatos;
  sha256?: string;
}

export interface ValidarPaqueteResult {
  ok: boolean;
  paquete?: RespaldoPaquete;
  errores: string[];
  avisos: string[];
}

export interface PlanImportacionPorFecha {
  fecha: string;
  nuevas: number;
  repetidas: number;
  salidas: number;
  curso: number;
  justificaciones: number;
  sinAlumno: number;
}

export interface PlanImportacion {
  asistencias: Asistencia[];
  salidas: { alumno_id: string; fecha: string; hora: string }[];
  cursos: { alumno_id: string; curso_id?: string; fecha: string; hora: string; origen: string }[];
  justificaciones: { alumno_id: string; fecha: string; tipo: TipoJustificacion | string; motivo: string }[];
  desconocidos: string[];
  cursosSinMatch: string[];
  porFecha: PlanImportacionPorFecha[];
  resumen: {
    asistencias: number;
    salidas: number;
    cursos: number;
    justificaciones: number;
    repetidas: number;
    desconocidos: number;
  };
}

export interface CtxImportacion {
  alumnos: Alumno[];
  cursos: Curso[];
  existentes: {
    asistencias: Map<string, Asistencia>;
    cursos: Set<string>;
    just: Set<string>;
  };
}

export interface LoadAllResult {
  alumnos: Alumno[];
  niveles: Nivel[];
  grados: Grado[];
  comunicados: Comunicado[];
  docentes: Docente[];
  cursos: Curso[];
  ajustes?: {
    periodos?: Periodo[];
    calendario?: DiaCalendario[];
    horarios?: Horario[];
    [key: string]: unknown;
  };
  [key: string]: unknown;
}

export interface Api {
  mode: 'demo' | 'supabase' | 'local' | 'error' | string;
  error?: unknown;
  init(): Promise<{ id: string; email?: string | null } | null>;
  signIn(email?: string, password?: string): Promise<{ id: string; email?: string | null } | null | unknown>;
  signOut(): Promise<void>;
  userId(): Promise<string | null | undefined>;
  getProfile(user?: unknown): Promise<Perfil>;
  horaServidor(): Promise<number>;
  reload?(): void;
  reset?(): void;

  getCodigoRegistro(cid?: string | null): Promise<string | null>;
  setCodigoRegistro(cid: string | null | undefined, codigo: string): Promise<void>;
  fotoUrl(alumno: { id?: string; foto_path?: string | null; foto_data?: string | null }): Promise<string | null>;

  loadAll(cid?: string | null): Promise<LoadAllResult>;
  asistenciasRango(cid: string | null | undefined, desde: string, hasta: string): Promise<Asistencia[]>;
  asistenciasPorFecha(cid: string | null | undefined, fecha: string): Promise<Asistencia[]>;
  asistenciasAlumno(alumnoId: string, limite?: number): Promise<Asistencia[]>;

  registrarAsistencia(row: Partial<Asistencia> & { alumno_id: string; fecha: string; hora: string }): Promise<void>;
  registrarMasivo(rows: (Partial<Asistencia> & { alumno_id: string; fecha: string })[]): Promise<number>;
  registrarSalidas(rows: { alumno_id: string; fecha: string; hora: string }[]): Promise<{ ok: number; dup: number; sin_entrada: number }>;

  save(tabla: string, data: Record<string, unknown>, id?: string | number): Promise<void>;
  remove(tabla: string, id: string | number): Promise<void>;
  upsertAlumnos(rows: Record<string, unknown>[]): Promise<number>;

  ajustesLista(cid?: string | null): Promise<{ periodos: Periodo[]; calendario: DiaCalendario[]; horarios: Horario[] }>;
  auditoriaLista(cid?: string | null, opts?: { limite?: number; tabla?: string; accion?: string }): Promise<Auditoria[]>;
  cursosLista(cid?: string | null): Promise<Curso[]>;
  justificacionesRango(cid: string | null | undefined, desde: string, hasta: string): Promise<Justificacion[]>;
  guardarJustificacion(row: Partial<Justificacion> & { alumno_id: string; fecha: string }): Promise<void>;
  eliminarJustificacion(id: string | number): Promise<void>;
  asistenciasCursoPorFecha(cid: string | null | undefined, cursoId: string, fecha: string): Promise<AsistenciaCurso[]>;
  registrarAsistenciaCurso(row: Partial<AsistenciaCurso> & { alumno_id: string; curso_id: string; fecha: string }): Promise<void>;
  registrarMasivoCurso(rows: (Partial<AsistenciaCurso> & { alumno_id: string; curso_id: string; fecha: string })[]): Promise<number>;
  avisosPorFecha(cid: string | null | undefined, fecha: string): Promise<AvisoApoderado[]>;
  registrarAviso(row: Partial<AvisoApoderado>): Promise<void>;
  registrarError(row: ErrorLogItem): Promise<void>;
  erroresRecientes(cid?: string | null, limite?: number): Promise<unknown[]>;
  borrarErrores(cid?: string | null): Promise<void>;
  exportarTodo(cid?: string | null): Promise<Record<string, unknown[]>>;
  eliminarFotoAlumno(alumno: { foto_path?: string | null }): Promise<void>;

  personalListar(): Promise<any[]>;
  personalAsignar(email: string, rol: string, carrera?: string | null, nombre?: string | null): Promise<void>;
  personalCrear(datos: { nombre: string; email: string; password?: string; rol: string; carrera?: string | null }): Promise<unknown>;
  personalPassword(id: string, password?: string): Promise<unknown>;
  personalEliminar(id: string): Promise<unknown>;
  personalQuitar(id: string): Promise<void>;
  personalDirectorio(): Promise<unknown[] | null>;
  actualizarMiPerfil(nombre: string): Promise<void>;
  subirFotoPerfil(userId: string, blob: Blob): Promise<string>;
  fotoPersonalUrl(path?: string | null): Promise<string | null>;
  tokenAvisos(): Promise<string | null>;
  solicitarRecuperacion(email: string, redirectTo?: string): Promise<void>;
  cambiarPassword(password: string): Promise<void>;
  alRecuperar(cb: () => void): void;

  esSuperadmin(): Promise<boolean>;
  saListar(): Promise<any[]>;
  saCrear(nombre: string, codigo?: string | null): Promise<any>;
  saRenombrar(id: string, nombre: string): Promise<void>;
  saActivar(id: string, activo: boolean): Promise<void>;
  saEntrar(id: string): Promise<any>;
  saAsignarAdmin(id: string, email: string): Promise<any>;
  renombrarInstituto(cid: string, nombre: string): Promise<void>;

  estadoSolicitud?(token: string): Promise<{ aprobado?: boolean } | null>;
}

declare global {
  interface Window {
    escritorio?: boolean;
    __simOffline?: boolean;
    __compatFallo?: boolean;
    AndroidBridge?: AndroidBridge;
  }
  var escritorio: boolean | undefined;
  var __simOffline: boolean | undefined;
  var __compatFallo: boolean | undefined;
  var AndroidBridge: AndroidBridge | undefined;
}
