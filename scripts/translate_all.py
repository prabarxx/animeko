#!/usr/bin/env python3
import xml.etree.ElementTree as ET
import re
import os

SRC_PATH = "app/shared/app-lang/src/androidMain/res/values/strings.xml"
DEST_PATH = "app/shared/app-lang/src/androidMain/res/values-es/strings.xml"

MAP = {
    # Onboarding
    "onboarding_welcome_title": "Bienvenido a Animeko",
    "onboarding_welcome_description": "Tu plataforma todo en uno para buscar, seguir y ver anime con comentarios en pantalla.",
    "onboarding_step_theme_title": "Elige tu estilo",
    "onboarding_step_theme_description": "Selecciona el tema que mejor se adapte a ti.",
    "onboarding_step_bangumi_title": "Conectar con Bangumi",
    "onboarding_step_bangumi_description": "Sincroniza tu lista de seguimiento, puntuaciones e historial en la nube.",
    "onboarding_step_sources_title": "Fuentes de datos",
    "onboarding_step_sources_description": "Configura tus fuentes favoritas para reproducir al instante.",
    "onboarding_button_next": "Siguiente",
    "onboarding_button_skip": "Omitir",
    "onboarding_button_start": "Comenzar",

    # Exploration & Home
    "exploration_tab_trending": "Tendencias",
    "exploration_tab_calendar": "Calendario",
    "exploration_tab_timeline": "Cronología",
    "exploration_tab_tags": "Etiquetas",
    "exploration_tab_ranking": "Clasificación",
    "exploration_calendar_monday": "Lunes",
    "exploration_calendar_tuesday": "Martes",
    "exploration_calendar_wednesday": "Miércoles",
    "exploration_calendar_thursday": "Jueves",
    "exploration_calendar_friday": "Viernes",
    "exploration_calendar_saturday": "Sábado",
    "exploration_calendar_sunday": "Domingo",
    "exploration_search_hint": "Buscar animes, personajes, autores...",
    "exploration_search_empty": "No se encontraron resultados",
    "exploration_search_history": "Historial de búsqueda",
    "exploration_clear_history": "Borrar historial",

    # Rating & Reviews
    "rating_score_1": "Muy malo",
    "rating_score_2": "Malo",
    "rating_score_3": "Mediocre",
    "rating_score_4": "Decente",
    "rating_score_5": "Aceptable",
    "rating_score_6": "Bueno",
    "rating_score_7": "Notable",
    "rating_score_8": "Muy bueno",
    "rating_score_9": "Excelente",
    "rating_score_10": "Obra maestra",
    "rating_my_score": "Mi puntuación: %1$d",
    "rating_rate_this": "Puntuar",
    "rating_clear_rating": "Eliminar puntuación",

    # Comments & Community
    "comment_input_hint": "Escribe un comentario...",
    "comment_send": "Publicar",
    "comment_reply": "Responder",
    "comment_like": "Me gusta",
    "comment_delete_confirm": "¿Eliminar este comentario?",
    "comment_empty_title": "Aún no hay comentarios",
    "comment_empty_description": "Sé el primero en compartir tu opinión.",

    # Auth & Login
    "login_bangumi": "Iniciar sesión en Bangumi",
    "login_ani": "Iniciar sesión con cuenta Ani",
    "login_username": "Nombre de usuario",
    "login_password": "Contraseña",
    "login_button": "Iniciar sesión",
    "login_logout": "Cerrar sesión",
    "login_logging_in": "Iniciando sesión...",
    "login_success": "Sesión iniciada con éxito",
    "login_failed": "Error al iniciar sesión: %1$s",
    "oauth_login_with_browser": "Iniciar sesión en el navegador",
    "oauth_waiting_for_callback": "Esperando autorización...",
    "oauth_auth_success": "Autorización completada",

    # Cache & Downloads
    "cache_status_idle": "Sin descargas activas",
    "cache_status_downloading": "Descargando (%1$d%%)",
    "cache_status_completed": "Descarga completada",
    "cache_status_failed": "Error en la descarga",
    "cache_status_paused": "En pausa",
    "cache_action_pause": "Pausar",
    "cache_action_resume": "Reanudar",
    "cache_action_delete": "Eliminar archivo",
    "cache_clear_all": "Borrar todo",
    "cache_storage_used": "Espacio usado: %1$s",
    "cache_storage_free": "Espacio libre: %1$s",
    "cache_auto_cache_enabled": "Caché automática activada",

    # Foundation & Network
    "foundation_network_error": "Error de conexión de red",
    "foundation_timeout_error": "Tiempo de espera agotado",
    "foundation_server_error": "Error del servidor (%1$d)",
    "foundation_retry": "Reintentar",
    "foundation_network_unavailable": "Sin conexión a Internet",

    # Mediafetch & Selector
    "mediafetch_searching": "Buscando fuentes...",
    "mediafetch_no_resources": "No se encontraron recursos disponibles",
    "mediafetch_timeout": "Tiempo de búsqueda agotado",
    "mediafetch_sources_queried": "%1$d fuentes consultadas",
    "media_source_online": "En línea",
    "media_source_bt": "BitTorrent",
    "media_source_selector_name": "AnimeOnline Ninja",
    "media_source_selector_description": "Anime en streaming con subtítulos y doblaje latino",
}

# Regex transformations for general strings
PATTERNS = [
    (r"\bSearch for items\b", "Buscar animes"),
    (r"\bSearch for episodes\b", "Buscar episodios"),
    (r"\bMatch videos\b", "Coincidencia de videos"),
    (r"\bCopy item link\b", "Copiar enlace del anime"),
    (r"\bOpen item page\b", "Abrir página del anime"),
    (r"\bTest data source\b", "Probar fuente de datos"),
    (r"\bExtract the episode list\b", "Extraer lista de episodios"),
    (r"\bExtract a list of\b", "Extraer lista de"),
    (r"\bWhen selecting resources in the player\b", "Al seleccionar recursos en el reproductor"),
    (r"\bWhen playing videos\b", "Al reproducir videos"),
    (r"\bDistinguish item names\b", "Distinguir nombres de animes"),
    (r"\bDistinguish channel names\b", "Distinguir nombres de canales"),
    (r"\bMark resolution\b", "Marcar resolución"),
    (r"\bMark subtitle language\b", "Marcar idioma de subtítulos"),
    (r"\bFilter by item name\b", "Filtrar por nombre del anime"),
    (r"\bFilter by episode number\b", "Filtrar por número de episodio"),
    (r"\bSearch request interval\b", "Intervalo entre peticiones de búsqueda"),
    (r"\bSearch cache duration\b", "Duración de la caché de búsqueda"),
    (r"\bNumber of item names to try\b", "Cantidad de nombres de anime a probar"),
    (r"\bUse only the first word\b", "Usar solo la primera palabra"),
    (r"\bRemove special characters\b", "Eliminar caracteres especiales"),
    (r"\bEnable nested links\b", "Habilitar enlaces anidados"),
    (r"\bMatch nested links\b", "Coincidir enlaces anidados"),
    (r"\bMatch video link\b", "Coincidir enlace de video"),
    (r"\bSingle tag\b", "Etiqueta única"),
    (r"\bMultiple tags\b", "Múltiples etiquetas"),
    (r"\bDo not distinguish channels\b", "No distinguir canales"),
    (r"\bGroup by channels\b", "Agrupar por canales"),
    (r"\bBase URL \(optional\)\b", "URL base (opcional)"),
    (r"\bSet the name shown in the list\b", "Establece el nombre mostrado en la lista"),
    (r"\bIcon URL\b", "URL del icono"),
    (r"\bCookies \(optional\)\b", "Cookies (opcional)"),
    (r"\bName \*\b", "Nombre *"),
    (r"\bchannels\b", "canales"),
    (r"\bchannel\b", "canal"),
]

def clean_xml_text(s):
    # Escape single quotes and ampersands if not already escaped
    return s.replace("'", "\\'")

def translate(key, text):
    if not text:
        return text
    # Check exact dictionary first
    if key in MAP:
        return clean_xml_text(MAP[key])
    
    t = text
    for p, r in PATTERNS:
        t = re.sub(p, r, t)
    return clean_xml_text(t)

def run():
    tree = ET.parse(SRC_PATH)
    root = tree.getroot()
    
    with open(SRC_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()
        
    out = []
    str_pat = re.compile(r'^(?P<ind>\s*)<string\s+name="(?P<name>[^"]+)"(?P<extra>[^>]*)>(?P<val>.*)</string>(?P<trail>\s*)$')
    
    count = 0
    for l in lines:
        m = str_pat.match(l)
        if m:
            ind = m.group("ind")
            name = m.group("name")
            extra = m.group("extra")
            val = m.group("val")
            trail = m.group("trail")
            
            if 'translatable="false"' in extra:
                out.append(l)
                continue
                
            tr = translate(name, val)
            if tr != val:
                count += 1
            out.append(f'{ind}<string name="{name}"{extra}>{tr}</string>{trail}\n')
        else:
            out.append(l)
            
    with open(DEST_PATH, "w", encoding="utf-8") as f:
        f.writelines(out)
        
    print(f"Updated {DEST_PATH}: {count} strings translated")

if __name__ == "__main__":
    run()
