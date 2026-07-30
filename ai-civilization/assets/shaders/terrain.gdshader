shader_type canvas_item;

// Террейн острова считается на GPU по маленькой текстуре данных 192x192,
// а не запекается в картинку 3072x3072.
//
// Почему так: попиксельное запекание давало либо блочную карту (1 пиксель на
// тайл, растянутый в 16 раз), либо секунды простоя на старте и десяток мегабайт
// текстуры при суперсэмплинге. Здесь в текстуру пишутся только высота и
// насыщенность лома, всё остальное — полосы палитры, затенение по градиенту,
// прибой, дизеринг — вычисляется во фрагментном шейдере. Стоимость на кадр
// незначительна, зато берег остаётся гладким на любом зуме.

uniform sampler2D data_tex : filter_linear, repeat_disable;
uniform vec2 data_size = vec2(192.0, 192.0);

// Высота хранится нормированной в 0..1 по фактическому диапазону карты:
// в RGBA8 всего 256 уровней, и без нормировки на сушу приходилось бы меньше
// сотни, что давало видимые ступени на склонах.
uniform float height_min = 0.0;
uniform float height_max = 1.0;
uniform float sea_level = 0.46;

uniform float tile_px = 16.0;
uniform float shade_strength = 34.0;
uniform float dither_amount = 0.010;
uniform float surf_speed = 0.6;

uniform vec3 c_deep : source_color = vec3(0.039, 0.082, 0.149);
uniform vec3 c_mid : source_color = vec3(0.063, 0.165, 0.267);
uniform vec3 c_shallow : source_color = vec3(0.090, 0.251, 0.361);
uniform vec3 c_surf : source_color = vec3(0.239, 0.498, 0.588);
uniform vec3 c_wet : source_color = vec3(0.290, 0.267, 0.212);
uniform vec3 c_sand : source_color = vec3(0.420, 0.380, 0.275);
uniform vec3 c_scrub : source_color = vec3(0.231, 0.290, 0.204);
uniform vec3 c_grass : source_color = vec3(0.275, 0.345, 0.235);
uniform vec3 c_dirt : source_color = vec3(0.333, 0.286, 0.184);
uniform vec3 c_rock : source_color = vec3(0.373, 0.353, 0.322);
uniform vec3 c_rock_hi : source_color = vec3(0.478, 0.455, 0.420);
uniform vec3 c_rust : source_color = vec3(0.420, 0.271, 0.153);
uniform vec3 c_shore_line : source_color = vec3(0.141, 0.122, 0.094);


float denorm(float v) {
	return height_min + v * (height_max - height_min);
}

float height_at(vec2 uv) {
	return denorm(texture(data_tex, uv).r);
}

// Упорядоченный дизеринг 4x4 в мировых координатах: привязка к миру, а не к
// экрану, иначе узор «плывёт» при панорамировании камеры.
float bayer(vec2 world_px) {
	vec2 p = floor(mod(world_px, 4.0));
	float i = p.y * 4.0 + p.x;
	float t = 0.0;
	if (i < 1.0) { t = 0.0; }
	else if (i < 2.0) { t = 8.0; }
	else if (i < 3.0) { t = 2.0; }
	else if (i < 4.0) { t = 10.0; }
	else if (i < 5.0) { t = 12.0; }
	else if (i < 6.0) { t = 4.0; }
	else if (i < 7.0) { t = 14.0; }
	else if (i < 8.0) { t = 6.0; }
	else if (i < 9.0) { t = 3.0; }
	else if (i < 10.0) { t = 11.0; }
	else if (i < 11.0) { t = 1.0; }
	else if (i < 12.0) { t = 9.0; }
	else if (i < 13.0) { t = 15.0; }
	else if (i < 14.0) { t = 7.0; }
	else if (i < 15.0) { t = 13.0; }
	else { t = 5.0; }
	return t / 16.0 - 0.5;
}

float hash21(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec3 water_color(float h, vec2 uv, vec2 world_px) {
	// Нормировка по узкой полосе: за шельфом вода сразу глубокая, иначе
	// полутон расползается на пол-карты и читается как грязная дымка.
	float depth = clamp((sea_level - h) / max(0.0001, sea_level * 0.10), 0.0, 1.0);
	vec3 col;
	if (depth < 0.10) {
		col = mix(c_surf, c_shallow, depth / 0.10);
	} else if (depth < 0.45) {
		col = mix(c_shallow, c_mid, (depth - 0.10) / 0.35);
	} else {
		col = mix(c_mid, c_deep, (depth - 0.45) / 0.55);
	}
	// Блики на мелководье: движение воды у берега, дешёвое и заметное.
	float shimmer = sin((world_px.x + world_px.y) * 0.05 + TIME * surf_speed)
		* sin(world_px.x * 0.031 - TIME * surf_speed * 0.7);
	col += vec3(0.055) * shimmer * (1.0 - depth) * (1.0 - depth);
	return col;
}

vec3 land_color(float h, float junk, vec2 uv, vec2 world_px) {
	float above = h - sea_level;
	vec3 col;
	if (above < 0.012) {
		col = c_wet;
	} else if (above < 0.035) {
		col = mix(c_wet, c_sand, (above - 0.012) / 0.023);
	} else if (above < 0.10) {
		col = mix(c_sand, c_scrub, (above - 0.035) / 0.065);
	} else if (above < 0.20) {
		col = mix(c_scrub, c_grass, (above - 0.10) / 0.10);
	} else if (above < 0.30) {
		col = mix(c_grass, c_dirt, (above - 0.20) / 0.10);
	} else {
		col = mix(c_rock, c_rock_hi, clamp((above - 0.30) / 0.20, 0.0, 1.0));
	}

	// Свалка: чем богаче поле лома, тем ржавее поверхность. Это остров-полигон
	// списания, ржавчина должна читаться прямо на грунте.
	if (junk > 0.62) {
		col = mix(col, c_rust, clamp((junk - 0.62) / 0.25, 0.0, 0.8));
		// Крупка металла: редкие светлые точки на ржавых участках.
		float speck = hash21(floor(world_px * 0.5));
		if (speck > 0.991) {
			col += vec3(0.13, 0.12, 0.10);
		}
	}

	// Затенение по градиенту высоты — свет с северо-запада. Именно это даёт
	// объём, которого не было у плоской заливки по типу тайла.
	vec2 texel = 1.0 / data_size;
	float dx = height_at(uv + vec2(texel.x, 0.0)) - height_at(uv - vec2(texel.x, 0.0));
	float dy = height_at(uv + vec2(0.0, texel.y)) - height_at(uv - vec2(0.0, texel.y));
	float shade = clamp(1.0 + (-dx - dy) * shade_strength, 0.55, 1.55);
	col *= shade;

	// Тёмная кромка у самой воды — остров получает силуэт.
	if (above < 0.006) {
		col = mix(col, c_shore_line, 0.45);
	}
	return col;
}

void fragment() {
	vec2 uv = UV;
	vec4 data = texture(data_tex, uv);
	vec2 world_px = uv * data_size * tile_px;

	float h = denorm(data.r);
	float d = bayer(world_px) * dither_amount;

	vec3 col;
	if (h < sea_level) {
		col = water_color(h + d, uv, world_px);
	} else {
		col = land_color(h + d * 0.35, data.g, uv, world_px);
	}
	COLOR = vec4(col, 1.0);
}
