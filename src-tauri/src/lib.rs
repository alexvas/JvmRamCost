use std::sync::Arc;
use tauri::{http::Uri, State};
use tokio::sync::OnceCell;
use tonic::transport::Channel;
use Jmvram::app_backend_client::AppBackendClient;

#[derive(Debug, thiserror::Error)]
enum Error {
    #[error(transparent)]
    Io(#[from] std::io::Error),
    #[error("failed to parse as string: {0}")]
    Utf8(#[from] std::str::Utf8Error),
    #[error(transparent)]
    InvalidUri(#[from] tonic::transport::Error),
    #[error(transparent)]
    GrpcStatus(#[from] tonic::Status),
    #[error("unsupported OS")]
    UnsupportedOS,
}

#[derive(serde::Serialize)]
#[serde(tag = "kind", content = "message")]
#[serde(rename_all = "camelCase")]
enum ErrorKind {
    Io(String),
    Utf8(String),
    InvalidUri(String),
    GrpcStatus(String),
    UnsupportedOS(String),
}

impl serde::Serialize for Error {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::ser::Serializer,
    {
        let error_message = self.to_string();
        let error_kind = match self {
            Self::Io(_) => ErrorKind::Io(error_message),
            Self::Utf8(_) => ErrorKind::Utf8(error_message),
            Self::InvalidUri(_) => ErrorKind::InvalidUri(error_message),
            Self::GrpcStatus(_) => ErrorKind::GrpcStatus(error_message),
            Self::UnsupportedOS => ErrorKind::UnsupportedOS(error_message),
        };
        error_kind.serialize(serializer)
    }
}

const LOCALHOST_V4: IpAddr = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
const GRPC_SERVER_PORT: u16 = 53535;
const INITIAL_RETRY_DELAY_MS: u64 = 1;
const MAX_RETRY_DELAY_MS: u64 = 1000;

use std::net::{IpAddr, Ipv4Addr};

fn create_grpc_client() -> AppBackendClient<Channel> {
    let uri = format!("http://{}:{}", LOCALHOST_V4, GRPC_SERVER_PORT)
        .parse::<Uri>()
        .expect("Invalid URI");
    let endpoint = tonic::transport::Endpoint::from(uri);
    let channel = endpoint.connect_lazy();
    AppBackendClient::new(channel)
}

use std::path::PathBuf;
struct AppState {
    client: OnceCell<AppBackendClient<Channel>>,
    backend_process: std::sync::Mutex<Option<std::process::Child>>,
    resource_dir: PathBuf,
    app_data_path: PathBuf,
}

impl AppState {
    fn new(resource_dir: PathBuf, app_data_path: PathBuf) -> Self {
        Self {
            client: OnceCell::new(),
            backend_process: std::sync::Mutex::new(None),
            resource_dir: resource_dir,
            app_data_path: app_data_path,
        }
    }

    async fn get_client(&self) -> AppBackendClient<Channel> {
        self.client
            .get_or_init(|| async { create_grpc_client() })
            .await
            .clone()
    }
}

mod backend;

impl AppState {
    fn kill_backend(&self) {
        if let Ok(mut process) = self.backend_process.lock() {
            if let Some(mut child) = process.take() {
                backend::kill(&mut child);
            }
        }
    }
}

impl Drop for AppState {
    fn drop(&mut self) {
        self.kill_backend();
    }
}

// Модуль google должен быть доступен через super:: из сгенерированного jvmram.rs
// Сгенерированный код находится в модуле jvmram (по имени package в proto)
pub mod google {
    pub mod protobuf {
        include!(concat!(env!("OUT_DIR"), "/google.protobuf.rs"));
    }
}

// Включаем сгенерированный код в модуль с именем пакета из proto (jvmram)
pub mod jvmram {
    include!(concat!(env!("OUT_DIR"), "/jvmram.rs"));
}

// Реэкспорт для удобства использования с правильным именем
#[allow(non_snake_case)]
pub mod Jmvram {
    pub use crate::jvmram::*;
}

async fn get_client(state: &State<'_, Arc<AppState>>) -> AppBackendClient<Channel> {
    state.get_client().await
}

use Jmvram::ApplicableMetricsResponse;

#[tauri::command]
async fn get_applicable_metrics(
    state: State<'_, Arc<AppState>>,
) -> Result<ApplicableMetricsResponse, Error> {
    let mut client = get_client(&state).await;
    let response = client.get_applicable_metrics(Empty::default()).await?;
    Ok(response.into_inner())
}

#[tauri::command]
async fn set_visible(
    state: State<'_, Arc<AppState>>,
    request: Jmvram::SetVisibleRequest,
) -> Result<(), Error> {
    let mut client = get_client(&state).await;
    client.set_visible(request).await?;
    Ok(())
}

#[tauri::command]
async fn set_invisible(
    state: State<'_, Arc<AppState>>,
    request: Jmvram::SetInvisibleRequest,
) -> Result<(), Error> {
    let mut client = get_client(&state).await;
    client.set_invisible(request).await?;
    Ok(())
}

#[tauri::command]
async fn set_following_pids(
    state: State<'_, Arc<AppState>>,
    request: Jmvram::PidList,
) -> Result<(), Error> {
    let mut client = get_client(&state).await;
    client.set_following_pids(request).await?;
    Ok(())
}

#[tauri::command]
async fn trigger_gc(state: State<'_, Arc<AppState>>, request: Jmvram::Pid) -> Result<(), Error> {
    let mut client = get_client(&state).await;
    client.trigger_gc(request).await?;
    Ok(())
}

#[tauri::command]
async fn read_yaml_config(state: State<'_, Arc<AppState>>) -> Result<String, Error> {
    let user_config_path = state.app_data_path.join("config.yaml");

    if !std::path::Path::new(&user_config_path).exists() {
        copy_default_config_to_user_config_path(&state.resource_dir, &user_config_path);
    }
    let config = std::fs::read_to_string(user_config_path).expect("Failed to read config file");
    Ok(config)
}

use chrono::Local;

fn prepare_output_file(
    prefix: &str,
    extension: &str,
    pid: i32,
    comment: &str,
    app_data_path: &PathBuf,
) -> PathBuf {
    let now = Local::now();
    let date_today = now.format("%Y-%m-%d").to_string();
    let output_dir = app_data_path.join("output").join(date_today);
    if !output_dir.exists() {
        std::fs::create_dir_all(&output_dir).expect("Failed to create output directory");
    }
    let process_output_dir = output_dir.join(pid.to_string());
    if !process_output_dir.exists() {
        std::fs::create_dir_all(&process_output_dir)
            .expect("Failed to create process output directory");
    }

    let date_time_now = now.format("%Y-%m-%d_%H_%M_%S").to_string();

    let file_name = if comment.is_empty() {
        format!("{}_{}_{}.{}", prefix, pid, date_time_now, extension)
    } else {
        format!(
            "{}_{}_{}_{}.{}",
            prefix, pid, date_time_now, comment, extension
        )
    };
    let output_file_path = process_output_dir.join(&file_name);
    return output_file_path;
}

#[tauri::command]
async fn heap_dump(
    state: State<'_, Arc<AppState>>,
    request: Jmvram::DumpRequest,
) -> Result<String, Error> {
    let output_file_path = prepare_output_file(
        "heap_dump",
        "hprof",
        request.pid,
        &request.comment,
        &state.app_data_path,
    );

    let jvm_request = Jmvram::DumpJvmRequest {
        pid: request.pid,
        output_file_path: output_file_path.to_string_lossy().to_string(),
    };

    let mut client = get_client(&state).await;
    client.dump_heap_jvm(jvm_request).await?;
    let file_name = output_file_path
        .file_name()
        .unwrap()
        .to_string_lossy()
        .to_string();
    Ok(file_name)
}

#[tauri::command]
async fn thread_dump(
    state: State<'_, Arc<AppState>>,
    request: Jmvram::DumpRequest,
) -> Result<String, Error> {
    let output_file_path = prepare_output_file(
        "thread_dump",
        "txt",
        request.pid,
        &request.comment,
        &state.app_data_path,
    );

    let jvm_request = Jmvram::DumpJvmRequest {
        pid: request.pid,
        output_file_path: output_file_path.to_string_lossy().to_string(),
    };

    let mut client = get_client(&state).await;
    client.dump_thread_jvm(jvm_request).await?;
    let file_name = output_file_path
        .file_name()
        .unwrap()
        .to_string_lossy()
        .to_string();
    Ok(file_name)
}

#[tauri::command]
async fn save_svg(
    state: State<'_, Arc<AppState>>,
    request: Jmvram::SvgSaveRequest,
) -> Result<String, Error> {
    let prefix = if request.auto {
        "metrics_auto"
    } else {
        "metrics"
    };
    let output_file_path = prepare_output_file(
        prefix, "svg", request.pid, &request.comment, &state.app_data_path,
    );
    std::fs::write(&output_file_path, request.content).expect("Failed to write SVG file");
    let file_name = output_file_path.file_name().unwrap().to_string_lossy().to_string();
    Ok(file_name)
}

fn copy_default_config_to_user_config_path(resource_dir: &PathBuf, user_config_path: &PathBuf) {
    let default_config_path = resource_dir.join("config.yaml");
    let user_config_dir = &user_config_path.parent().unwrap();
    if !user_config_dir.exists() {
        std::fs::create_dir_all(user_config_dir).expect("Failed to create user config directory");
    }
    std::fs::copy(default_config_path, user_config_path)
        .expect("Failed to copy default config file");
}

use crate::google::protobuf::Empty;
use std::time::Duration;
use tauri::{AppHandle, Emitter};

async fn listen_available_jvm_processes_updated(app: AppHandle, state: Arc<AppState>) {
    let mut retry_delay = Duration::from_millis(INITIAL_RETRY_DELAY_MS);

    loop {
        match try_listen_jvm_processes(&app, &state).await {
            Ok(()) => {
                // Стрим завершился нормально, сбрасываем таймаут и переподключаемся
                retry_delay = Duration::from_millis(INITIAL_RETRY_DELAY_MS);
            }
            Err(e) => {
                eprintln!(
                    "gRPC error (jvm_processes): {}, retry in {:?}",
                    e, retry_delay
                );
            }
        }
        tokio::time::sleep(retry_delay).await;
        retry_delay = std::cmp::min(retry_delay * 2, Duration::from_millis(MAX_RETRY_DELAY_MS));
    }
}

async fn try_listen_jvm_processes(app: &AppHandle, state: &Arc<AppState>) -> Result<(), Error> {
    let mut client = state.get_client().await;
    let response = client.listen_jvm_process_list(Empty::default()).await?;
    let mut stream = response.into_inner();

    while let Some(response) = stream.message().await? {
        app.emit("available-jvm-processes-updated", &response)
            .unwrap();
    }
    Ok(())
}

async fn listen_graph_queues(app: AppHandle, state: Arc<AppState>) {
    let mut retry_delay = Duration::from_millis(INITIAL_RETRY_DELAY_MS);

    loop {
        match try_listen_graph_queues(&app, &state).await {
            Ok(()) => {
                // Стрим завершился нормально, сбрасываем таймаут и переподключаемся
                retry_delay = Duration::from_millis(INITIAL_RETRY_DELAY_MS);
            }
            Err(e) => {
                eprintln!(
                    "gRPC error (graph_queues): {}, retry in {:?}",
                    e, retry_delay
                );
            }
        }
        tokio::time::sleep(retry_delay).await;
        retry_delay = std::cmp::min(retry_delay * 2, Duration::from_millis(MAX_RETRY_DELAY_MS));
    }
}

async fn try_listen_graph_queues(app: &AppHandle, state: &Arc<AppState>) -> Result<(), Error> {
    let mut client = state.get_client().await;
    let response = client.listen_graph_queues(Empty::default()).await?;
    let mut stream = response.into_inner();

    while let Some(response) = stream.message().await? {
        app.emit("graph-queues-updated", &response).unwrap();
    }
    Ok(())
}

use tauri::{App, Builder, Manager};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    Builder::default()
        .setup(|app| {
            setup_application_start(app).unwrap();
            Ok(())
        })
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            get_applicable_metrics,
            set_visible,
            set_invisible,
            set_following_pids,
            trigger_gc,
            read_yaml_config,
            heap_dump,
            thread_dump,
            save_svg,
        ])
        .on_window_event(|_window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                // Получаем state из app через window
                if let Some(state) = _window.app_handle().try_state::<Arc<AppState>>() {
                    state.kill_backend();
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

mod resource;

fn setup_application_start(app: &App) -> Result<(), Error> {
    let state = create_application_state(app)?;
    app.manage(state.clone());

    use crate::backend::start_backend;
    // Запускаем Java бэкенд
    let backend_child = match start_backend(&state.resource_dir) {
        Ok(child) => child,
        Err(e) => {
            eprintln!("Ошибка запуска бэкенда: {}", e);
            std::process::exit(1);
        }
    };

    *state.backend_process.lock().unwrap() = Some(backend_child);

    let state_copy = state.clone();
    let app_handle = app.handle().clone();
    tauri::async_runtime::spawn(async move {
        listen_available_jvm_processes_updated(app_handle, state_copy).await;
    });

    let state_copy = state.clone();
    let app_handle = app.handle().clone();
    tauri::async_runtime::spawn(async move {
        listen_graph_queues(app_handle, state_copy).await;
    });

    let state_copy = state.clone();
    // Обработчик сигналов для корректного завершения бэкенда при CTRL+C
    tauri::async_runtime::spawn(async move {
        let _ = setup_backend_shutdown(state_copy).await;
    });

    Ok(())
}

async fn setup_backend_shutdown(state: Arc<AppState>) -> Result<(), Error> {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{signal, SignalKind};
        let mut sigterm =
            signal(SignalKind::terminate()).expect("failed to install SIGTERM handler");
        let mut sigint = signal(SignalKind::interrupt()).expect("failed to install SIGINT handler");

        tokio::select! {
            _ = sigterm.recv() => {
                eprintln!("Получен SIGTERM, завершаем бэкенд...");
            }
            _ = sigint.recv() => {
                eprintln!("Получен SIGINT (CTRL+C), завершаем бэкенд...");
            }
        }
    }
    #[cfg(not(unix))]
    {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install CTRL+C handler");
        eprintln!("Получен CTRL+C, завершаем бэкенд...");
    }
    state.kill_backend();
    // Завершаем процесс после завершения бэкенда
    std::process::exit(0);
    #[allow(unreachable_code)]
    Ok(())
}

fn create_application_state(app: &App) -> Result<Arc<AppState>, Error> {
    let app_resource_dir = app.path().resource_dir().unwrap();

    use std::env::consts::OS;
    use std::env::var;
    // the application data is in the User's home directory (OS-dependent) ~/.jvm-ram-cost
    let app_data_path: PathBuf = match OS {
        "linux" => PathBuf::from(var("HOME").unwrap()).join(".jvm-ram-cost"),
        "windows" => PathBuf::from(var("APPDATA").unwrap()).join("jvm-ram-cost"),
        _ => return Err(Error::UnsupportedOS),
    };
    let resource_dir = resource::get_resource_dir(&app_resource_dir).unwrap();

    let state = Arc::new(AppState::new(resource_dir, app_data_path));
    Ok(state)
}
