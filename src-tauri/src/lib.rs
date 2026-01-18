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
}

#[derive(serde::Serialize)]
#[serde(tag = "kind", content = "message")]
#[serde(rename_all = "camelCase")]
enum ErrorKind {
    Io(String),
    Utf8(String),
    InvalidUri(String),
    GrpcStatus(String),
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

struct AppState {
    client: OnceCell<AppBackendClient<Channel>>,
    backend_process: std::sync::Mutex<Option<std::process::Child>>,
}

impl AppState {
    fn new() -> Self {
        Self {
            client: OnceCell::new(),
            backend_process: std::sync::Mutex::new(None),
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

use tauri::{AppHandle, Emitter};
use crate::google::protobuf::Empty;
use std::time::Duration;

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
        retry_delay = std::cmp::min(
            retry_delay * 2,
            Duration::from_millis(MAX_RETRY_DELAY_MS),
        );
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
        retry_delay = std::cmp::min(
            retry_delay * 2,
            Duration::from_millis(MAX_RETRY_DELAY_MS),
        );
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

use tauri::{Builder, Manager};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    Builder::default()
        .setup(|app| {
            // Запускаем Java бэкенд
            use crate::backend::start_backend;
            let backend_child = match start_backend(app) {
                Ok(child) => child,
                Err(e) => {
                    eprintln!("Ошибка запуска бэкенда: {}", e);
                    std::process::exit(1);
                }
            };

            let state = Arc::new(AppState::new());
            *state.backend_process.lock().unwrap() = Some(backend_child);
            app.manage(state.clone());

            let state2 = state.clone();

            let app_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                listen_available_jvm_processes_updated(app_handle, state).await;
            });
            let app_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                listen_graph_queues(app_handle, state2).await;
            });

            Ok(())
        })
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            get_applicable_metrics,
            set_visible,
            set_invisible,
            set_following_pids,
            trigger_gc,
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
