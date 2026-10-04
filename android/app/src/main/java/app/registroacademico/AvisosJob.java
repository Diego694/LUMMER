package app.registroacademico;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Tarea periódica del sistema: consulta comunicados nuevos aunque la app esté cerrada. */
public class AvisosJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            Avisos.consultar(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
